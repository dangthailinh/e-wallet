package com.github.yildizmy.auth;

import com.github.yildizmy.domain.entity.User;
import com.github.yildizmy.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class RefreshSessionRepositoryIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private RefreshSessionRepository refreshSessionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void storesOnlyTheTokenHashAndFindsAnActiveSessionForUpdate() {
        User user = userRepository.findByUsername("johndoe").orElseThrow();
        String rawToken = "refresh-token-that-must-never-be-persisted";
        RefreshTokenHash tokenHash = RefreshTokenHash.fromToken(rawToken);
        Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        RefreshSession session = new RefreshSession(
                user,
                UUID.randomUUID(),
                tokenHash,
                createdAt,
                createdAt.plus(30, ChronoUnit.DAYS)
        );

        RefreshSession saved = refreshSessionRepository.saveAndFlush(session);

        String storedHash = jdbcTemplate.queryForObject(
                "select token_hash from refresh_session where id = ?",
                String.class,
                session.getId()
        );
        assertThat(saved).isSameAs(session);
        assertThat(saved.getVersion()).isNotNull();
        assertThat(storedHash).isEqualTo(tokenHash.value()).isNotEqualTo(rawToken);
        assertThat(refreshSessionRepository.findActiveByTokenHashForUpdate(
                tokenHash,
                createdAt.plus(1, ChronoUnit.DAYS)
        )).contains(session);
    }

    @Test
    void activeLookupRejectsRotatedRevokedAndExpiredSessions() {
        User user = userRepository.findByUsername("johndoe").orElseThrow();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        UUID familyId = UUID.randomUUID();
        RefreshSession rotated = session(user, familyId, "rotated", now, now.plus(1, ChronoUnit.DAYS));
        RefreshSession replacement = session(user, familyId, "replacement", now.plusSeconds(10), now.plus(1, ChronoUnit.DAYS));
        rotated.rotateTo(replacement, now.plusSeconds(10));
        RefreshSession revoked = session(user, UUID.randomUUID(), "revoked", now, now.plus(1, ChronoUnit.DAYS));
        revoked.revoke("logout", now.plusSeconds(10));
        RefreshSession expired = session(user, UUID.randomUUID(), "expired", now.minus(2, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS));
        RefreshSession future = session(user, UUID.randomUUID(), "future", now.plus(1, ChronoUnit.DAYS), now.plus(2, ChronoUnit.DAYS));
        refreshSessionRepository.saveAllAndFlush(List.of(replacement, rotated, revoked, expired, future));

        assertThat(refreshSessionRepository.findActiveByTokenHashForUpdate(rotated.getTokenHash(), now)).isEmpty();
        assertThat(refreshSessionRepository.findActiveByTokenHashForUpdate(revoked.getTokenHash(), now)).isEmpty();
        assertThat(refreshSessionRepository.findActiveByTokenHashForUpdate(expired.getTokenHash(), now)).isEmpty();
        assertThat(refreshSessionRepository.findActiveByTokenHashForUpdate(future.getTokenHash(), now)).isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void activeLookupRechecksTheRowAfterAConcurrentRotationCommits() throws Exception {
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        RefreshTokenHash tokenHash = RefreshTokenHash.fromToken("concurrent-refresh-token");
        UUID familyId = UUID.randomUUID();
        UUID sessionId = transactions.execute(status -> {
            User user = userRepository.findByUsername("johndoe").orElseThrow();
            return refreshSessionRepository.saveAndFlush(new RefreshSession(
                    user,
                    familyId,
                    tokenHash,
                    now,
                    now.plus(1, ChronoUnit.DAYS)
            )).getId();
        });
        assertThat(sessionId).isNotNull();

        CountDownLatch firstLockAcquired = new CountDownLatch(1);
        CountDownLatch releaseFirstLock = new CountDownLatch(1);
        CountDownLatch secondQueryStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<UUID> first = executor.submit(() -> transactions.execute(status -> {
                RefreshSession locked = refreshSessionRepository
                        .findActiveByTokenHashForUpdate(tokenHash, now.plusSeconds(1))
                        .orElseThrow();
                RefreshSession replacement = new RefreshSession(
                        locked.getUser(),
                        familyId,
                        RefreshTokenHash.fromToken("concurrent-replacement-token"),
                        now.plusSeconds(2),
                        now.plus(2, ChronoUnit.DAYS)
                );
                refreshSessionRepository.save(replacement);
                locked.rotateTo(replacement, now.plusSeconds(2));
                refreshSessionRepository.flush();
                firstLockAcquired.countDown();
                await(releaseFirstLock);
                return locked.getId();
            }));
            assertThat(firstLockAcquired.await(5, TimeUnit.SECONDS)).isTrue();

            Future<Boolean> second = executor.submit(() -> transactions.execute(status -> {
                secondQueryStarted.countDown();
                return refreshSessionRepository
                        .findActiveByTokenHashForUpdate(tokenHash, now.plusSeconds(3))
                        .isEmpty();
            }));
            assertThat(secondQueryStarted.await(5, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(200);
            assertThat(second).isNotDone();

            releaseFirstLock.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo(sessionId);
            assertThat(second.get(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            releaseFirstLock.countDown();
            executor.shutdownNow();
            transactions.executeWithoutResult(status -> jdbcTemplate.update(
                    "delete from refresh_session where family_id = ?",
                    familyId
            ));
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void consumedLookupSerializesConcurrentReuseHandling() throws Exception {
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        RefreshTokenHash tokenHash = RefreshTokenHash.fromToken("consumed-refresh-token");
        UUID sessionId = transactions.execute(status -> {
            User user = userRepository.findByUsername("johndoe").orElseThrow();
            return refreshSessionRepository.saveAndFlush(new RefreshSession(
                    user,
                    UUID.randomUUID(),
                    tokenHash,
                    now,
                    now.plus(1, ChronoUnit.DAYS)
            )).getId();
        });
        assertThat(sessionId).isNotNull();

        CountDownLatch firstLockAcquired = new CountDownLatch(1);
        CountDownLatch releaseFirstLock = new CountDownLatch(1);
        CountDownLatch secondQueryStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<UUID> first = executor.submit(() -> transactions.execute(status -> {
                RefreshSession locked = refreshSessionRepository.findByTokenHashForUpdate(tokenHash).orElseThrow();
                firstLockAcquired.countDown();
                await(releaseFirstLock);
                return locked.getId();
            }));
            assertThat(firstLockAcquired.await(5, TimeUnit.SECONDS)).isTrue();

            Future<UUID> second = executor.submit(() -> transactions.execute(status -> {
                secondQueryStarted.countDown();
                return refreshSessionRepository.findByTokenHashForUpdate(tokenHash).orElseThrow().getId();
            }));
            assertThat(secondQueryStarted.await(5, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(200);
            assertThat(second).isNotDone();

            releaseFirstLock.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo(sessionId);
            assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo(sessionId);
        } finally {
            releaseFirstLock.countDown();
            executor.shutdownNow();
            transactions.executeWithoutResult(status -> refreshSessionRepository.deleteById(sessionId));
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rootMutexMakesReuseSeeAndRevokeAConcurrentlyCommittedSuccessor() throws Exception {
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        UUID familyId = UUID.randomUUID();
        String rootRawToken = "root-mutex-reused-token";
        String middleRawToken = "root-mutex-active-token";
        RefreshTokenHash rootHash = RefreshTokenHash.fromToken(rootRawToken);
        RefreshTokenHash middleHash = RefreshTokenHash.fromToken(middleRawToken);
        transactions.executeWithoutResult(status -> {
            User user = userRepository.findByUsername("johndoe").orElseThrow();
            RefreshSession root = session(user, familyId, rootRawToken, now, now.plus(1, ChronoUnit.DAYS));
            RefreshSession middle = session(
                    user,
                    familyId,
                    middleRawToken,
                    now.plusSeconds(1),
                    now.plus(2, ChronoUnit.DAYS)
            );
            root.rotateTo(middle, now.plusSeconds(1));
            refreshSessionRepository.saveAllAndFlush(List.of(middle, root));
        });

        CountDownLatch successorPrepared = new CountDownLatch(1);
        CountDownLatch allowRotationCommit = new CountDownLatch(1);
        CountDownLatch reuseStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<UUID> rotation = executor.submit(() -> transactions.execute(status -> {
                RefreshSession root = refreshSessionRepository
                        .findFamilyRootByTokenHashForUpdate(middleHash)
                        .orElseThrow();
                List<RefreshSession> family = refreshSessionRepository
                        .findFamilyByFamilyIdForUpdate(root.getFamilyId());
                RefreshSession middle = family.stream()
                        .filter(session -> session.getTokenHash().equals(middleHash))
                        .findFirst()
                        .orElseThrow();
                RefreshSession successor = session(
                        middle.getUser(),
                        familyId,
                        "root-mutex-successor-token",
                        now.plusSeconds(2),
                        now.plus(3, ChronoUnit.DAYS)
                );
                refreshSessionRepository.save(successor);
                middle.rotateTo(successor, now.plusSeconds(2));
                refreshSessionRepository.flush();
                successorPrepared.countDown();
                await(allowRotationCommit);
                return successor.getId();
            }));
            assertThat(successorPrepared.await(5, TimeUnit.SECONDS)).isTrue();

            Future<List<UUID>> reuse = executor.submit(() -> transactions.execute(status -> {
                reuseStarted.countDown();
                RefreshSession root = refreshSessionRepository
                        .findFamilyRootByTokenHashForUpdate(rootHash)
                        .orElseThrow();
                List<RefreshSession> family = refreshSessionRepository
                        .findFamilyByFamilyIdForUpdate(root.getFamilyId());
                family.forEach(session -> session.revoke("refresh-token-reuse", now.plusSeconds(3)));
                return family.stream().map(RefreshSession::getId).toList();
            }));
            assertThat(reuseStarted.await(5, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(200);
            assertThat(reuse).isNotDone();

            allowRotationCommit.countDown();
            UUID successorId = rotation.get(5, TimeUnit.SECONDS);
            List<UUID> revokedIds = reuse.get(5, TimeUnit.SECONDS);
            assertThat(revokedIds).hasSize(3).contains(successorId);
            Integer activeCount = transactions.execute(status -> jdbcTemplate.queryForObject(
                    "select count(*) from refresh_session where family_id = ? and revoked_at is null",
                    Integer.class,
                    familyId
            ));
            assertThat(activeCount).isZero();
        } finally {
            allowRotationCommit.countDown();
            executor.shutdownNow();
            transactions.executeWithoutResult(status -> jdbcTemplate.update(
                    "delete from refresh_session where family_id = ?",
                    familyId
            ));
        }
    }

    @Test
    void migrationCreatesFamilyExpiryAndActiveSessionIndexes() {
        java.util.List<String> indexDefinitions = jdbcTemplate.queryForList(
                "select indexdef from pg_indexes where schemaname = 'public' and tablename = 'refresh_session'",
                String.class
        );

        assertThat(indexDefinitions)
                .anyMatch(index -> index.contains("family_id"))
                .anyMatch(index -> index.contains("expires_at"))
                .anyMatch(index -> index.contains("token_hash") && index.contains("WHERE"));

        List<String> constraintNames = jdbcTemplate.queryForList(
                "select constraint_name from information_schema.table_constraints where table_name = 'refresh_session'",
                String.class
        );
        assertThat(constraintNames)
                .contains("fk_refresh_session_replacement", "uc_refresh_session_replacement", "ck_refresh_session_hash");

        List<String> triggerNames = jdbcTemplate.queryForList(
                "select tgname from pg_trigger where tgrelid = 'refresh_session'::regclass and not tgisinternal",
                String.class
        );
        assertThat(triggerNames)
                .contains("ck_refresh_session_replacement_time", "ck_refresh_session_immutable_identity");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void databaseRejectsAReplacementThatStartsOutsideTheRotationTime() {
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        UUID familyId = UUID.randomUUID();
        List<UUID> sessionIds = transactions.execute(status -> {
            User user = userRepository.findByUsername("johndoe").orElseThrow();
            RefreshSession current = session(user, familyId, "direct-current", now, now.plus(1, ChronoUnit.DAYS));
            RefreshSession successor = session(
                    user,
                    familyId,
                    "direct-successor",
                    now.plusSeconds(10),
                    now.plus(1, ChronoUnit.DAYS)
            );
            refreshSessionRepository.saveAllAndFlush(List.of(current, successor));
            return List.of(current.getId(), successor.getId());
        });
        assertThat(sessionIds).isNotNull();

        try {
            assertThatThrownBy(() -> transactions.executeWithoutResult(status -> jdbcTemplate.update(
                    "update refresh_session set rotated_at = ?, replaced_by_session_id = ? where id = ?",
                    now.plusSeconds(5).atOffset(ZoneOffset.UTC),
                    sessionIds.get(1),
                    sessionIds.get(0)
            )))
                    .hasRootCauseInstanceOf(java.sql.SQLException.class)
                    .rootCause()
                    .hasMessageContaining("successor must start at the rotation time");
        } finally {
            transactions.executeWithoutResult(status -> jdbcTemplate.update(
                    "delete from refresh_session where id in (?, ?)",
                    sessionIds.get(0),
                    sessionIds.get(1)
            ));
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void databaseRejectsTwoNodeReplacementCycles() {
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        UUID familyId = UUID.randomUUID();
        List<UUID> sessionIds = transactions.execute(status -> {
            User user = userRepository.findByUsername("johndoe").orElseThrow();
            RefreshSession first = session(user, familyId, "cycle-first", now, now.plus(1, ChronoUnit.DAYS));
            RefreshSession second = session(user, familyId, "cycle-second", now, now.plus(1, ChronoUnit.DAYS));
            refreshSessionRepository.saveAllAndFlush(List.of(first, second));
            return List.of(first.getId(), second.getId());
        });
        assertThat(sessionIds).isNotNull();

        try {
            assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
                jdbcTemplate.update(
                        "update refresh_session set rotated_at = ?, replaced_by_session_id = ? where id = ?",
                        now.atOffset(ZoneOffset.UTC),
                        sessionIds.get(1),
                        sessionIds.get(0)
                );
                jdbcTemplate.update(
                        "update refresh_session set rotated_at = ?, replaced_by_session_id = ? where id = ?",
                        now.atOffset(ZoneOffset.UTC),
                        sessionIds.get(0),
                        sessionIds.get(1)
                );
            }))
                    .hasRootCauseInstanceOf(java.sql.SQLException.class)
                    .rootCause()
                    .hasMessageContaining("ck_refresh_session_rotation_time");
        } finally {
            transactions.executeWithoutResult(status -> jdbcTemplate.update(
                    "delete from refresh_session where id in (?, ?)",
                    sessionIds.get(0),
                    sessionIds.get(1)
            ));
        }
    }

    private static RefreshSession session(
            User user,
            UUID familyId,
            String token,
            Instant createdAt,
            Instant expiresAt
    ) {
        return new RefreshSession(user, familyId, RefreshTokenHash.fromToken(token), createdAt, expiresAt);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for concurrent transaction");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for concurrent transaction", exception);
        }
    }
}

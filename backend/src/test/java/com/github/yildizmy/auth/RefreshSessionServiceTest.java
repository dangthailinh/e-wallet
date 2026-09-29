package com.github.yildizmy.auth;

import com.github.yildizmy.domain.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshSessionServiceTest {

    private static final Duration REFRESH_TTL = Duration.ofDays(30);
    private static final Instant START = Instant.parse("2026-09-17T09:00:00Z");

    @Mock
    private RefreshSessionRepository repository;

    private MutableClock clock;
    private RefreshSessionService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(START);
        service = new RefreshSessionService(repository, REFRESH_TTL, new SecureRandom(), clock);
    }

    @Test
    void issuesAnOpaqueTokenButPersistsOnlyItsHash() {
        User user = user(1L);
        when(repository.save(any(RefreshSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RefreshSessionService.RefreshToken issued = service.issue(user);

        ArgumentCaptor<RefreshSession> sessionCaptor = ArgumentCaptor.forClass(RefreshSession.class);
        verify(repository).save(sessionCaptor.capture());
        RefreshSession session = sessionCaptor.getValue();
        assertThat(issued.value()).hasSizeGreaterThanOrEqualTo(43);
        assertThat(issued.expiresAt()).isEqualTo(START.plus(REFRESH_TTL));
        assertThat(issued.userId()).isEqualTo(1L);
        assertThat(session.getTokenHash()).isEqualTo(RefreshTokenHash.fromToken(issued.value()));
        assertThat(session.getTokenHash().toString()).doesNotContain(issued.value());
        assertThat(issued.toString()).doesNotContain(issued.value()).contains("redacted");
    }

    @Test
    void rotatesAnActiveTokenAndLinksTheSuccessor() {
        User user = user(1L);
        UUID familyId = UUID.randomUUID();
        String currentRawToken = "current-opaque-refresh-token";
        RefreshSession current = session(user, familyId, currentRawToken, START);
        clock.advance(Duration.ofMinutes(1));
        RefreshTokenHash currentHash = RefreshTokenHash.fromToken(currentRawToken);
        when(repository.findFamilyRootByTokenHashForUpdate(currentHash))
                .thenReturn(java.util.Optional.of(current));
        when(repository.findFamilyByFamilyIdForUpdate(familyId)).thenReturn(List.of(current));
        when(repository.save(any(RefreshSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RefreshSessionService.RefreshToken rotated = service.rotate(currentRawToken);

        ArgumentCaptor<RefreshSession> successorCaptor = ArgumentCaptor.forClass(RefreshSession.class);
        verify(repository).save(successorCaptor.capture());
        RefreshSession successor = successorCaptor.getValue();
        assertThat(successor.getFamilyId()).isEqualTo(familyId);
        assertThat(successor.getTokenHash()).isEqualTo(RefreshTokenHash.fromToken(rotated.value()));
        assertThat(successor.getCreatedAt()).isEqualTo(clock.instant());
        assertThat(current.getRotatedAt()).isEqualTo(clock.instant());
        assertThat(current.getReplacedBySession()).isSameAs(successor);
        assertThat(rotated.userId()).isEqualTo(1L);
        var lockOrder = inOrder(repository);
        lockOrder.verify(repository).findFamilyRootByTokenHashForUpdate(currentHash);
        lockOrder.verify(repository).findFamilyByFamilyIdForUpdate(familyId);
        lockOrder.verify(repository).save(successor);
    }

    @Test
    void reuseRevokesTheWholeFamilyAndCommitsThatRevocationBeforeReturningAnError() throws NoSuchMethodException {
        User user = user(1L);
        UUID familyId = UUID.randomUUID();
        String reusedRawToken = "already-rotated-token";
        RefreshSession consumed = session(user, familyId, reusedRawToken, START);
        RefreshSession successor = session(user, familyId, "successor-token", START.plusSeconds(1));
        consumed.rotateTo(successor, START.plusSeconds(1));
        clock.advance(Duration.ofSeconds(2));
        RefreshTokenHash reusedHash = RefreshTokenHash.fromToken(reusedRawToken);
        when(repository.findFamilyRootByTokenHashForUpdate(reusedHash))
                .thenReturn(java.util.Optional.of(consumed));
        when(repository.findFamilyByFamilyIdForUpdate(familyId)).thenReturn(List.of(consumed, successor));

        assertThatThrownBy(() -> service.rotate(reusedRawToken))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Refresh token is invalid");

        assertThat(consumed.getRevokedAt()).isEqualTo(clock.instant());
        assertThat(successor.getRevokedAt()).isEqualTo(clock.instant());
        assertThat(consumed.getRevocationReason()).isEqualTo("refresh-token-reuse");
        Transactional transaction = RefreshSessionService.class
                .getMethod("rotate", String.class)
                .getAnnotation(Transactional.class);
        assertThat(transaction.noRollbackFor()).contains(BadCredentialsException.class);
        assertThat(transaction.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

    @Test
    void unknownOrMerelyExpiredTokensDoNotRevokeAFamily() {
        User user = user(1L);
        String expiredRawToken = "expired-token";
        RefreshSession expired = session(user, UUID.randomUUID(), expiredRawToken, START);
        clock.advance(REFRESH_TTL.plusSeconds(1));
        RefreshTokenHash expiredHash = RefreshTokenHash.fromToken(expiredRawToken);
        when(repository.findFamilyRootByTokenHashForUpdate(expiredHash))
                .thenReturn(java.util.Optional.of(expired));
        when(repository.findFamilyByFamilyIdForUpdate(expired.getFamilyId())).thenReturn(List.of(expired));

        assertThatThrownBy(() -> service.rotate(expiredRawToken))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Refresh token is invalid");
        assertThat(expired.getRevokedAt()).isNull();
    }

    @Test
    void logoutRevokesTheWholeSessionFamily() {
        User user = user(1L);
        UUID familyId = UUID.randomUUID();
        String presentedRawToken = "presented-logout-token";
        RefreshSession root = session(user, familyId, presentedRawToken, START);
        RefreshSession successor = session(user, familyId, "active-successor", START.plusSeconds(1));
        root.rotateTo(successor, START.plusSeconds(1));
        clock.advance(Duration.ofSeconds(2));
        RefreshTokenHash presentedHash = RefreshTokenHash.fromToken(presentedRawToken);
        when(repository.findFamilyRootByTokenHashForUpdate(presentedHash))
                .thenReturn(java.util.Optional.of(root));
        when(repository.findFamilyByFamilyIdForUpdate(familyId)).thenReturn(List.of(root, successor));

        service.revoke(presentedRawToken);

        assertThat(root.getRevocationReason()).isEqualTo("logout");
        assertThat(successor.getRevocationReason()).isEqualTo("logout");
        assertThat(root.getRevokedAt()).isEqualTo(clock.instant());
        assertThat(successor.getRevokedAt()).isEqualTo(clock.instant());
    }

    private static User user(long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private static RefreshSession session(User user, UUID familyId, String rawToken, Instant createdAt) {
        return new RefreshSession(
                user,
                familyId,
                RefreshTokenHash.fromToken(rawToken),
                createdAt,
                createdAt.plus(REFRESH_TTL)
        );
    }

    private static final class MutableClock extends Clock {

        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        private void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}

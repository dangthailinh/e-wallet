package com.github.yildizmy.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshSessionRepository extends JpaRepository<RefreshSession, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select session
            from RefreshSession session
            where session.tokenHash = :tokenHash
              and session.createdAt <= :now
              and session.expiresAt > :now
              and session.rotatedAt is null
              and session.revokedAt is null
            """)
    Optional<RefreshSession> findActiveByTokenHashForUpdate(
            @Param("tokenHash") RefreshTokenHash tokenHash,
            @Param("now") Instant now
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from RefreshSession session where session.tokenHash = :tokenHash")
    Optional<RefreshSession> findByTokenHashForUpdate(@Param("tokenHash") RefreshTokenHash tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select root
            from RefreshSession root
            where root.familyId = (
                select presented.familyId
                from RefreshSession presented
                where presented.tokenHash = :tokenHash
            )
              and root.createdAt = (
                select min(candidate.createdAt)
                from RefreshSession candidate
                where candidate.familyId = root.familyId
              )
            """)
    Optional<RefreshSession> findFamilyRootByTokenHashForUpdate(@Param("tokenHash") RefreshTokenHash tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select session
            from RefreshSession session
            where session.familyId = :familyId
            order by session.createdAt, session.id
            """)
    List<RefreshSession> findFamilyByFamilyIdForUpdate(@Param("familyId") UUID familyId);
}

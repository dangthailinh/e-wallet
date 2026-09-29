package com.github.yildizmy.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    @Query("""
            select event from AuditLog event
            where (:actorUserId is null or event.actorUserId = :actorUserId)
              and (:action is null or event.action = :action)
              and (:resourceType is null or event.resourceType = :resourceType)
              and (:resourceId is null or event.resourceId = :resourceId)
            """)
    Page<AuditLog> findForAdmin(
            @Param("actorUserId") Long actorUserId,
            @Param("action") String action,
            @Param("resourceType") String resourceType,
            @Param("resourceId") UUID resourceId,
            Pageable pageable
    );
}

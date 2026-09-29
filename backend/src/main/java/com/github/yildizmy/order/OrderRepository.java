package com.github.yildizmy.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByUserIdAndIdempotencyKey(long userId, String idempotencyKey);

    Optional<Order> findByIdAndUserId(UUID id, long userId);

    Page<Order> findByUserId(long userId, Pageable pageable);

    @EntityGraph(attributePaths = "user")
    @Query("""
            select orderEntity from Order orderEntity
            where (:status is null or orderEntity.status = :status)
              and (:userId is null or orderEntity.user.id = :userId)
            """)
    Page<Order> findForAdmin(
            @Param("status") OrderStatus status,
            @Param("userId") Long userId,
            Pageable pageable
    );
}

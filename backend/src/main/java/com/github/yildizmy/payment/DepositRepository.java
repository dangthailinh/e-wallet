package com.github.yildizmy.payment;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DepositRepository extends JpaRepository<Deposit, UUID> {

    Optional<Deposit> findByIdAndUserId(UUID id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select deposit from Deposit deposit
            join fetch deposit.wallet
            where deposit.providerCode = :providerCode
              and deposit.merchantReference = :merchantReference
            """)
    Optional<Deposit> lockByProviderAndReference(
            @Param("providerCode") String providerCode,
            @Param("merchantReference") String merchantReference
    );

    @EntityGraph(attributePaths = {"user", "wallet"})
    @Query("""
            select deposit from Deposit deposit
            where (:status is null or deposit.status = :status)
              and (:channel is null or deposit.channel = :channel)
              and (:userId is null or deposit.user.id = :userId)
            """)
    Page<Deposit> findForAdmin(
            @Param("status") DepositStatus status,
            @Param("channel") DepositChannel channel,
            @Param("userId") Long userId,
            Pageable pageable
    );
}

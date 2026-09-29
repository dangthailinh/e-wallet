package com.github.yildizmy.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO webhook_events (
                id, provider_code, provider_event_id, provider_transaction_id, request_hash
            ) VALUES (
                :id, :providerCode, :providerEventId, :providerTransactionId, :requestHash
            )
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int insertReceived(
            @Param("id") UUID id,
            @Param("providerCode") String providerCode,
            @Param("providerEventId") String providerEventId,
            @Param("providerTransactionId") String providerTransactionId,
            @Param("requestHash") byte[] requestHash
    );

    Optional<WebhookEvent> findByProviderCodeAndProviderEventId(String providerCode, String providerEventId);

    Optional<WebhookEvent> findByProviderCodeAndProviderTransactionId(
            String providerCode,
            String providerTransactionId
    );
}

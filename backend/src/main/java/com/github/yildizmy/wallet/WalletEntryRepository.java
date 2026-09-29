package com.github.yildizmy.wallet;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WalletEntryRepository extends JpaRepository<WalletEntry, UUID> {

    Optional<WalletEntry> findByBusinessReference(String businessReference);

    Page<WalletEntry> findByWalletId(Long walletId, Pageable pageable);
}

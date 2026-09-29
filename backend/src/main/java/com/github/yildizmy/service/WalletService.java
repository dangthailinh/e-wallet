package com.github.yildizmy.service;

import com.github.yildizmy.config.MessageSourceConfig;
import com.github.yildizmy.domain.entity.Wallet;
import com.github.yildizmy.dto.mapper.WalletResponseMapper;
import com.github.yildizmy.dto.response.WalletResponse;
import com.github.yildizmy.exception.NoSuchElementFoundException;
import com.github.yildizmy.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.github.yildizmy.common.MessageKeys.ERROR_NO_RECORDS;
import static com.github.yildizmy.common.MessageKeys.ERROR_WALLET_NOT_FOUND;

/**
 * Service used for Wallet related operations.
 */
@Service
@RequiredArgsConstructor
public class WalletService {

    private final MessageSourceConfig messageConfig;
    private final WalletRepository walletRepository;
    private final WalletResponseMapper walletResponseMapper;

    /**
     * Fetches a single wallet by the given id.
     *
     * @param id
     * @return WalletResponse
     */
    @Transactional(readOnly = true)
    public WalletResponse findById(long id) {
        return walletRepository.findById(id)
                .map(walletResponseMapper::toWalletResponse)
                .orElseThrow(() -> new NoSuchElementFoundException(messageConfig.getMessage(ERROR_WALLET_NOT_FOUND)));
    }

    /**
     * Fetches a single wallet by the given iban.
     *
     * @param iban
     * @return WalletResponse
     */
    @Transactional(readOnly = true)
    public WalletResponse findByIban(String iban) {
        return walletRepository.findByIban(iban)
                .map(walletResponseMapper::toWalletResponse)
                .orElseThrow(() -> new NoSuchElementFoundException(messageConfig.getMessage(ERROR_WALLET_NOT_FOUND)));
    }

    /**
     * Fetches a single wallet by the given userId.
     *
     * @param userId
     * @return WalletResponse
     */
    @Transactional(readOnly = true)
    public List<WalletResponse> findByUserId(long userId) {
        return walletRepository.findByUserId(userId).stream()
                .map(walletResponseMapper::toWalletResponse)
                .toList();
    }

    /**
     * Fetches a single wallet reference (entity) by the given id.
     *
     * @param iban
     * @return Wallet
     */
    public Wallet getByIban(String iban) {
        return walletRepository.findByIban(iban)
                .orElseThrow(() -> new NoSuchElementFoundException(messageConfig.getMessage(ERROR_WALLET_NOT_FOUND)));
    }

    /**
     * Fetches all wallets based on the given paging and sorting parameters.
     *
     * @param pageable
     * @return List of WalletResponse
     */
    @Transactional(readOnly = true)
    public Page<WalletResponse> findAll(Pageable pageable) {
        final Page<Wallet> wallets = walletRepository.findAll(pageable);
        if (wallets.isEmpty())
            throw new NoSuchElementFoundException(messageConfig.getMessage(ERROR_NO_RECORDS));
        return wallets.map(walletResponseMapper::toWalletResponse);
    }

}

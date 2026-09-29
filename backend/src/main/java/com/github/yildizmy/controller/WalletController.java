package com.github.yildizmy.controller;

import com.github.yildizmy.dto.response.WalletResponse;
import com.github.yildizmy.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/wallets")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    /**
     * Fetches a single wallet by the given id.
     *
     * @param id
     * @return WalletResponse wrapped by ResponseEntity<T>
     */
    @GetMapping("/{id}")
    public ResponseEntity<WalletResponse> findById(@PathVariable long id) {
        final WalletResponse response = walletService.findById(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Fetches a single wallet by the given iban.
     *
     * @param iban
     * @return WalletResponse wrapped by ResponseEntity<T>
     */
    @GetMapping("/iban/{iban}")
    public ResponseEntity<WalletResponse> findByIban(@PathVariable String iban) {
        final WalletResponse response = walletService.findByIban(iban);
        return ResponseEntity.ok(response);
    }

    /**
     * Fetches a single wallet by the given userId.
     *
     * @param userId
     * @return WalletResponse wrapped by ResponseEntity<T>
     */
    @GetMapping("/users/{userId}")
    public ResponseEntity<List<WalletResponse>> findByUserId(@PathVariable long userId) {
        final List<WalletResponse> response = walletService.findByUserId(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Fetches all wallets based on the given paging and sorting parameters.
     *
     * @param pageable
     * @return List of WalletResponse wrapped by ResponseEntity<T>
     */
    @GetMapping
    public ResponseEntity<Page<WalletResponse>> findAll(Pageable pageable) {
        final Page<WalletResponse> response = walletService.findAll(pageable);
        return ResponseEntity.ok(response);
    }

}

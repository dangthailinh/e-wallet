package com.github.yildizmy.wallet;

import com.github.yildizmy.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('USER')")
@RequestMapping(value = "/api/v1/wallet", produces = MediaType.APPLICATION_JSON_VALUE)
public class WalletQueryController {

    private final WalletQueryService service;

    @GetMapping
    public ResponseEntity<WalletView> currentWallet(@AuthenticationPrincipal UserDetailsImpl currentUser) {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(service.currentWallet(currentUser.getId()));
    }

    @GetMapping("/entries")
    public ResponseEntity<WalletEntriesPage> entries(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        if (page < 0 || page > 1_000 || size < 1 || size > 50) {
            throw new InvalidWalletQueryException("page must be 0..1000 and size must be 1..50");
        }
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(WalletEntriesPage.from(service.entries(currentUser.getId(), page, size)));
    }
}

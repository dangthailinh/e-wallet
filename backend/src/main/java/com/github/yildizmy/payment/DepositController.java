package com.github.yildizmy.payment;

import com.github.yildizmy.security.UserDetailsImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('USER')")
@RequestMapping(value = "/api/v1/deposits", produces = MediaType.APPLICATION_JSON_VALUE)
public class DepositController {

    private final DepositService deposits;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DepositResponse> create(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @Valid @RequestBody CreateDepositRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(deposits.create(currentUser.getId(), request));
    }

    @GetMapping("/{depositId}")
    public ResponseEntity<DepositResponse> status(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID depositId
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(deposits.status(currentUser.getId(), depositId));
    }
}

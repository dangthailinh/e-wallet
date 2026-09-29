package com.github.yildizmy.order;

import com.github.yildizmy.config.RequestIdFilter;
import com.github.yildizmy.security.UserDetailsImpl;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('USER')")
@RequestMapping(value = "/api/v1/orders", produces = MediaType.APPLICATION_JSON_VALUE)
public class OrderController {

    private final CheckoutService checkout;
    private final OrderQueryService queries;
    private final SecretRevealService reveal;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OrderCheckoutResponse> create(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @RequestHeader("Idempotency-Key")
            @Pattern(regexp = "[A-Za-z0-9_-]{8,100}") String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request
    ) {
        CheckoutResult result = checkout.checkout(
                currentUser.getId(), request.productId(), request.expectedPriceVnd(), idempotencyKey
        );
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .body(OrderCheckoutResponse.from(result));
    }

    @GetMapping
    public ResponseEntity<OrderListResponse> list(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(queries.list(currentUser.getId(), page, size));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderDetailResponse> detail(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID orderId
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(queries.detail(currentUser.getId(), orderId));
    }

    @PostMapping("/{orderId}/items/{itemId}/reveal")
    public ResponseEntity<SecretRevealResponse> reveal(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @PathVariable UUID orderId,
            @PathVariable UUID itemId,
            HttpServletRequest request
    ) {
        String requestId = (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(reveal.reveal(currentUser.getId(), orderId, itemId, requestId));
    }
}

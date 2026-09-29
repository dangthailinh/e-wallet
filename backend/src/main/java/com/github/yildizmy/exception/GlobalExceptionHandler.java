package com.github.yildizmy.exception;

import com.github.yildizmy.config.RequestIdFilter;
import com.github.yildizmy.inventory.InventoryStateConflictException;
import com.github.yildizmy.admin.InvalidAdminOperationsQueryException;
import com.github.yildizmy.order.IdempotencyConflictException;
import com.github.yildizmy.order.InvalidOrderQueryException;
import com.github.yildizmy.order.OutOfStockException;
import com.github.yildizmy.order.ProductPriceChangedException;
import com.github.yildizmy.payment.PaymentChannelUnavailableException;
import com.github.yildizmy.payment.ReconciliationRateLimitException;
import com.github.yildizmy.payment.momo.MomoProviderException;
import com.github.yildizmy.payment.UnsupportedPaymentAmountException;
import com.github.yildizmy.wallet.InvalidWalletQueryException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String INTERNAL_ERROR_DETAIL = "An unexpected error occurred.";

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request
    ) {
        Map<String, List<String>> errors = exception.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.groupingBy(
                        FieldError::getField,
                        LinkedHashMap::new,
                        Collectors.mapping(FieldError::getDefaultMessage, Collectors.toList())
                ));
        return problem(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "VALIDATION_FAILED",
                "Request validation failed.",
                errors,
                request
        );
    }

    @ExceptionHandler(NoSuchElementFoundException.class)
    public ResponseEntity<Object> handleNoSuchElementFoundException(
            NoSuchElementFoundException exception,
            WebRequest request
    ) {
        return domainProblem(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", exception, request);
    }

    @ExceptionHandler(ElementAlreadyExistsException.class)
    public ResponseEntity<Object> handleElementAlreadyExistsException(
            ElementAlreadyExistsException exception,
            WebRequest request
    ) {
        return domainProblem(HttpStatus.CONFLICT, "RESOURCE_CONFLICT", exception, request);
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<Object> handleInsufficientFundsException(
            InsufficientFundsException exception,
            WebRequest request
    ) {
        return domainProblem(HttpStatus.PRECONDITION_FAILED, "INSUFFICIENT_FUNDS", exception, request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Object> handleAuthenticationException(
            AuthenticationException exception,
            WebRequest request
    ) {
        log.warn("Authentication rejected requestId={}", requestId(request));
        return problem(
                HttpStatus.UNAUTHORIZED,
                "AUTHENTICATION_REQUIRED",
                "Authentication is required.",
                null,
                request
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDeniedException(
            AccessDeniedException exception,
            WebRequest request
    ) {
        log.warn("Access denied requestId={}", requestId(request));
        return problem(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Access is denied.", null, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintValidationException(
            ConstraintViolationException exception,
            WebRequest request
    ) {
        Map<String, List<String>> errors = exception.getConstraintViolations().stream()
                .collect(Collectors.groupingBy(
                        violation -> violation.getPropertyPath().toString(),
                        LinkedHashMap::new,
                        Collectors.mapping(violation -> violation.getMessage(), Collectors.toList())
                ));
        return problem(
                HttpStatus.BAD_REQUEST,
                "CONSTRAINT_VIOLATION",
                "Request constraints were not satisfied.",
                errors,
                request
        );
    }

    @ExceptionHandler(InvalidCatalogQueryException.class)
    public ResponseEntity<Object> handleInvalidCatalogQueryException(
            InvalidCatalogQueryException exception,
            WebRequest request
    ) {
        return domainProblem(HttpStatus.BAD_REQUEST, "INVALID_CATALOG_QUERY", exception, request);
    }

    @ExceptionHandler(InvalidAdminOperationsQueryException.class)
    public ResponseEntity<Object> handleInvalidAdminOperationsQueryException(
            InvalidAdminOperationsQueryException exception,
            WebRequest request
    ) {
        return domainProblem(HttpStatus.BAD_REQUEST, "INVALID_ADMIN_OPERATIONS_QUERY", exception, request);
    }

    @ExceptionHandler(InvalidWalletQueryException.class)
    public ResponseEntity<Object> handleInvalidWalletQueryException(
            InvalidWalletQueryException exception,
            WebRequest request
    ) {
        return domainProblem(HttpStatus.BAD_REQUEST, "INVALID_WALLET_QUERY", exception, request);
    }

    @ExceptionHandler(InvalidOrderQueryException.class)
    public ResponseEntity<Object> handleInvalidOrderQueryException(
            InvalidOrderQueryException exception,
            WebRequest request
    ) {
        return domainProblem(HttpStatus.BAD_REQUEST, "INVALID_ORDER_QUERY", exception, request);
    }

    @ExceptionHandler(OutOfStockException.class)
    public ResponseEntity<Object> handleOutOfStockException(
            OutOfStockException exception,
            WebRequest request
    ) {
        return domainProblem(HttpStatus.CONFLICT, "OUT_OF_STOCK", exception, request);
    }

    @ExceptionHandler(ProductPriceChangedException.class)
    public ResponseEntity<Object> handleProductPriceChangedException(
            ProductPriceChangedException exception,
            WebRequest request
    ) {
        return domainProblem(HttpStatus.CONFLICT, "PRODUCT_PRICE_CHANGED", exception, request);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<Object> handleIdempotencyConflictException(
            IdempotencyConflictException exception,
            WebRequest request
    ) {
        return domainProblem(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", exception, request);
    }

    @ExceptionHandler(InventoryStateConflictException.class)
    public ResponseEntity<Object> handleInventoryStateConflictException(
            InventoryStateConflictException exception,
            WebRequest request
    ) {
        return domainProblem(HttpStatus.CONFLICT, "INVENTORY_STATE_CONFLICT", exception, request);
    }

    @ExceptionHandler(PaymentChannelUnavailableException.class)
    public ResponseEntity<Object> handlePaymentChannelUnavailableException(
            PaymentChannelUnavailableException exception,
            WebRequest request
    ) {
        return domainProblem(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_CHANNEL_UNAVAILABLE", exception, request);
    }

    @ExceptionHandler(MomoProviderException.class)
    public ResponseEntity<Object> handleMomoProviderException(
            MomoProviderException exception,
            WebRequest request
    ) {
        log.warn("MoMo provider request failed requestId={}", requestId(request));
        return domainProblem(HttpStatus.BAD_GATEWAY, "PAYMENT_PROVIDER_ERROR", exception, request);
    }

    @ExceptionHandler(UnsupportedPaymentAmountException.class)
    public ResponseEntity<Object> handleUnsupportedPaymentAmountException(
            UnsupportedPaymentAmountException exception,
            WebRequest request
    ) {
        return domainProblem(HttpStatus.UNPROCESSABLE_ENTITY, "PAYMENT_AMOUNT_UNSUPPORTED", exception, request);
    }

    @ExceptionHandler(ReconciliationRateLimitException.class)
    public ResponseEntity<Object> handleReconciliationRateLimit(
            ReconciliationRateLimitException exception,
            WebRequest request
    ) {
        ResponseEntity<Object> base = problem(
                HttpStatus.TOO_MANY_REQUESTS,
                "RECONCILIATION_RATE_LIMITED",
                "Provider re-query rate limit exceeded.",
                null,
                request
        );
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(exception.getRetryAfterSeconds()))
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(base.getBody());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleAllUncaughtException(Exception exception, WebRequest request) {
        log.error(
                "Unhandled exception type={} requestId={}",
                exception.getClass().getName(),
                requestId(request)
        );
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", INTERNAL_ERROR_DETAIL, null, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception,
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request
    ) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        if (status == null || status.is5xxServerError()) {
            log.error(
                    "Internal framework exception type={} requestId={}",
                    exception.getClass().getName(),
                    requestId(request)
            );
            return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", INTERNAL_ERROR_DETAIL, null, request);
        }
        return problem(status, "REQUEST_FAILED", status.getReasonPhrase(), null, request);
    }

    private ResponseEntity<Object> domainProblem(
            HttpStatus status,
            String code,
            RuntimeException exception,
            WebRequest request
    ) {
        log.warn("Domain error code={} requestId={}", code, requestId(request));
        return problem(status, code, exception.getMessage(), null, request);
    }

    private ResponseEntity<Object> problem(
            HttpStatus status,
            String code,
            String detail,
            Map<String, List<String>> errors,
            WebRequest request
    ) {
        ErrorResponse response = new ErrorResponse(
                URI.create("urn:problem:" + code.toLowerCase(Locale.ROOT).replace('_', '-')),
                status.getReasonPhrase(),
                status.value(),
                detail,
                requestUri(request),
                code,
                requestId(request),
                errors
        );
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(response);
    }

    private URI requestUri(WebRequest request) {
        String description = request.getDescription(false);
        String path = description.startsWith("uri=") ? description.substring(4) : description;
        int queryStart = path.indexOf('?');
        return URI.create(queryStart >= 0 ? path.substring(0, queryStart) : path);
    }

    private String requestId(WebRequest request) {
        Object requestId = request.getAttribute(
                RequestIdFilter.REQUEST_ID_ATTRIBUTE,
                RequestAttributes.SCOPE_REQUEST
        );
        return requestId instanceof String value ? value : "unknown";
    }
}

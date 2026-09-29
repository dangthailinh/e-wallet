package com.github.yildizmy.admin;

import com.github.yildizmy.order.OrderRepository;
import com.github.yildizmy.order.OrderStatus;
import com.github.yildizmy.payment.DepositChannel;
import com.github.yildizmy.payment.DepositRepository;
import com.github.yildizmy.payment.DepositStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminOperationsQueryService {

    private final OrderRepository orders;
    private final DepositRepository deposits;

    public AdminPageResponse<AdminOrderResponse> listOrders(
            int page,
            int size,
            String status,
            Long userId
    ) {
        PageRequest pageable = pageable(page, size);
        OrderStatus orderStatus = parseOrderStatus(status);
        validateUserId(userId);
        return AdminPageResponse.from(orders.findForAdmin(orderStatus, userId, pageable)
                .map(AdminOrderResponse::from));
    }

    public AdminPageResponse<AdminDepositResponse> listDeposits(
            int page,
            int size,
            String status,
            String channel,
            Long userId
    ) {
        PageRequest pageable = pageable(page, size);
        DepositStatus depositStatus = parseDepositStatus(status);
        DepositChannel depositChannel = parseDepositChannel(channel);
        validateUserId(userId);
        return AdminPageResponse.from(deposits.findForAdmin(depositStatus, depositChannel, userId, pageable)
                .map(AdminDepositResponse::from));
    }

    private PageRequest pageable(int page, int size) {
        if (page < 0 || page > 10_000 || size < 1 || size > 100) {
            throw new InvalidAdminOperationsQueryException();
        }
        return PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
    }

    private OrderStatus parseOrderStatus(String value) {
        if (value == null) return null;
        try {
            return OrderStatus.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new InvalidAdminOperationsQueryException();
        }
    }

    private DepositStatus parseDepositStatus(String value) {
        if (value == null) return null;
        try {
            return DepositStatus.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new InvalidAdminOperationsQueryException();
        }
    }

    private DepositChannel parseDepositChannel(String value) {
        if (value == null) return null;
        try {
            return DepositChannel.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new InvalidAdminOperationsQueryException();
        }
    }

    private void validateUserId(Long userId) {
        if (userId != null && userId < 1) {
            throw new InvalidAdminOperationsQueryException();
        }
    }
}

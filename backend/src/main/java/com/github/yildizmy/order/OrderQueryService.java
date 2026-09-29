package com.github.yildizmy.order;

import com.github.yildizmy.exception.NoSuchElementFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderQueryService {

    private final OrderRepository orders;
    private final OrderItemRepository orderItems;

    @Transactional(readOnly = true)
    public OrderListResponse list(long userId, int page, int size) {
        validatePage(page, size);
        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
        Page<OrderSummaryResponse> result = orders.findByUserId(userId, pageable)
                .map(OrderSummaryResponse::from);
        return OrderListResponse.from(result);
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse detail(long userId, UUID orderId) {
        Order order = orders.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new NoSuchElementFoundException("Order not found"));
        List<OrderItem> items = orderItems.findAllByOrderIdOrderById(orderId);
        return OrderDetailResponse.from(order, items);
    }

    private void validatePage(int page, int size) {
        if (page < 0 || page > 1_000 || size < 1 || size > 50) {
            throw new InvalidOrderQueryException();
        }
    }
}

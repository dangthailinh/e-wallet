package com.github.yildizmy.admin;

import com.github.yildizmy.order.OrderRepository;
import com.github.yildizmy.payment.DepositRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AdminOperationsQueryServiceTest {

    @Mock
    private OrderRepository orders;

    @Mock
    private DepositRepository deposits;

    @InjectMocks
    private AdminOperationsQueryService queryService;

    @Test
    void rejectsInvalidOrderFiltersBeforeTouchingRepositories() {
        assertThatThrownBy(() -> queryService.listOrders(0, 20, "NOT_A_STATUS", null))
                .isInstanceOf(InvalidAdminOperationsQueryException.class);
        assertThatThrownBy(() -> queryService.listOrders(-1, 20, null, null))
                .isInstanceOf(InvalidAdminOperationsQueryException.class);
        assertThatThrownBy(() -> queryService.listOrders(0, 101, null, null))
                .isInstanceOf(InvalidAdminOperationsQueryException.class);
        assertThatThrownBy(() -> queryService.listOrders(0, 20, null, 0L))
                .isInstanceOf(InvalidAdminOperationsQueryException.class);

        verifyNoInteractions(orders);
    }

    @Test
    void rejectsInvalidDepositFiltersBeforeTouchingRepositories() {
        assertThatThrownBy(() -> queryService.listDeposits(0, 20, "NOT_A_STATUS", null, null))
                .isInstanceOf(InvalidAdminOperationsQueryException.class);
        assertThatThrownBy(() -> queryService.listDeposits(0, 20, null, "BANK", 0L))
                .isInstanceOf(InvalidAdminOperationsQueryException.class);
        assertThatThrownBy(() -> queryService.listDeposits(0, 20, null, "CARD", null))
                .isInstanceOf(InvalidAdminOperationsQueryException.class);

        verifyNoInteractions(deposits);
    }
}

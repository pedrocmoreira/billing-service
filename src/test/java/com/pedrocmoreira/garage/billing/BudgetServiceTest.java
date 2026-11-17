package com.pedrocmoreira.garage.billing;

import com.pedrocmoreira.garage.billing.application.BudgetService;
import com.pedrocmoreira.garage.billing.domain.exception.BusinessException;
import com.pedrocmoreira.garage.billing.domain.exception.EntityNotFoundException;
import com.pedrocmoreira.garage.billing.domain.model.*;
import com.pedrocmoreira.garage.billing.domain.port.BudgetNotifier;
import com.pedrocmoreira.garage.billing.domain.port.MessagePublisher;
import com.pedrocmoreira.garage.billing.domain.port.PaymentGateway;
import com.pedrocmoreira.garage.billing.domain.saga.SagaMessage;
import com.pedrocmoreira.garage.billing.infrastructure.persistence.BudgetRepository;
import com.pedrocmoreira.garage.billing.infrastructure.persistence.PaymentRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Optional;

import static com.pedrocmoreira.garage.billing.domain.saga.MessageTypes.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BudgetServiceTest {
    private BudgetRepository budgetRepository;
    private PaymentRepository paymentRepository;
    private PaymentGateway gateway;
    private BudgetNotifier notifier;
    private MessagePublisher publisher;
    private BudgetService service;

    @BeforeEach
    void setUp() {
        budgetRepository = mock(BudgetRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        gateway = mock(PaymentGateway.class);
        notifier = mock(BudgetNotifier.class);
        publisher = mock(MessagePublisher.class);
        service = new BudgetService(budgetRepository, paymentRepository, gateway, notifier, publisher, new SimpleMeterRegistry());
        when(budgetRepository.save(any())).thenAnswer(inv -> {
            Budget b = inv.getArgument(0);
            if (b.getId() == null) b.setId(5L);
            return b;
        });
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private SagaMessage lastPublished() {
        ArgumentCaptor<SagaMessage> captor = ArgumentCaptor.forClass(SagaMessage.class);
        verify(publisher, atLeastOnce()).publish(captor.capture());
        return captor.getValue();
    }

    @Test
    void createShouldBuildBudgetSendEmailAndPublishEvent() {
        when(budgetRepository.findByServiceOrderId(100L)).thenReturn(Optional.empty());

        Budget budget = service.create(BillingFixtures.createBudgetCommand());

        assertThat(budget.getTotalAmount()).isEqualByComparingTo("302.00");
        assertThat(budget.getItems()).hasSize(2);
        assertThat(budget.getStatus()).isEqualTo(BudgetStatus.PENDING_APPROVAL);
        verify(notifier).sendBudget(budget);
        assertThat(lastPublished().type()).isEqualTo(EVT_BUDGET_CREATED);
    }

    @Test
    void createWithoutItemsKeepsZeroTotal() {
        when(budgetRepository.findByServiceOrderId(100L)).thenReturn(Optional.empty());
        Budget budget = service.create(SagaMessage.of(CMD_BILLING_CREATE_BUDGET, 100L, "OS", Map.of("customerName", "A")));
        assertThat(budget.getTotalAmount()).isZero();
    }

    @Test
    void createIsIdempotent() {
        Budget existing = BillingFixtures.budget();
        when(budgetRepository.findByServiceOrderId(100L)).thenReturn(Optional.of(existing));
        assertThat(service.create(BillingFixtures.createBudgetCommand())).isSameAs(existing);
        verifyNoInteractions(notifier);
        assertThat(lastPublished().type()).isEqualTo(EVT_BUDGET_CREATED);
    }

    @Test
    void approveShouldCreateCheckoutAndPublish() {
        Budget budget = BillingFixtures.budget();
        when(budgetRepository.findByPublicToken("token-123")).thenReturn(Optional.of(budget));
        when(gateway.createCheckout(budget)).thenReturn(new PaymentGateway.CheckoutSession("pref-1", "http://pay"));
        when(gateway.providerName()).thenReturn("MERCADO_PAGO");

        Payment payment = service.approve("token-123");

        assertThat(budget.getStatus()).isEqualTo(BudgetStatus.APPROVED);
        assertThat(payment.getCheckoutUrl()).isEqualTo("http://pay");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        SagaMessage event = lastPublished();
        assertThat(event.type()).isEqualTo(EVT_BUDGET_APPROVED);
        assertThat(event.dataAsString("paymentUrl")).isEqualTo("http://pay");
    }

    @Test
    void approveTwiceShouldFail() {
        Budget budget = BillingFixtures.budget();
        budget.setStatus(BudgetStatus.APPROVED);
        when(budgetRepository.findByPublicToken("token-123")).thenReturn(Optional.of(budget));
        assertThatThrownBy(() -> service.approve("token-123")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.reject("token-123")).isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectShouldPublishEvent() {
        Budget budget = BillingFixtures.budget();
        when(budgetRepository.findByPublicToken("token-123")).thenReturn(Optional.of(budget));
        service.reject("token-123");
        assertThat(budget.getStatus()).isEqualTo(BudgetStatus.REJECTED);
        assertThat(lastPublished().type()).isEqualTo(EVT_BUDGET_REJECTED);
    }

    @Test
    void unknownTokenShouldFail() {
        when(budgetRepository.findByPublicToken("x")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findByToken("x")).isInstanceOf(EntityNotFoundException.class);
        when(budgetRepository.findByServiceOrderId(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findByServiceOrder(1L)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void cancelShouldCancelBudgetAndPendingPayment() {
        Budget budget = BillingFixtures.budget();
        budget.setStatus(BudgetStatus.APPROVED);
        Payment payment = BillingFixtures.payment(budget);
        when(budgetRepository.findByServiceOrderId(100L)).thenReturn(Optional.of(budget));
        when(paymentRepository.findByBudgetId(5L)).thenReturn(Optional.of(payment));

        service.cancel(SagaMessage.of(CMD_BILLING_CANCEL_BUDGET, 100L, "OS", Map.of()));

        assertThat(budget.getStatus()).isEqualTo(BudgetStatus.CANCELLED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(lastPublished().type()).isEqualTo(EVT_BUDGET_CANCELLED);
        assertThat(service.findByServiceOrder(100L)).isSameAs(budget);
    }

    @Test
    void cancelPaidBudgetKeepsStatus() {
        Budget budget = BillingFixtures.budget();
        budget.setStatus(BudgetStatus.PAID);
        when(budgetRepository.findByServiceOrderId(100L)).thenReturn(Optional.of(budget));
        service.cancel(SagaMessage.of(CMD_BILLING_CANCEL_BUDGET, 100L, "OS", Map.of()));
        assertThat(budget.getStatus()).isEqualTo(BudgetStatus.PAID);
    }

    @Test
    void cancelWithoutBudgetIsNoop() {
        when(budgetRepository.findByServiceOrderId(100L)).thenReturn(Optional.empty());
        service.cancel(SagaMessage.of(CMD_BILLING_CANCEL_BUDGET, 100L, "OS", Map.of()));
        verifyNoInteractions(publisher);
    }

    @Test
    void markPaidRequiresApproval() {
        Budget budget = BillingFixtures.budget();
        assertThatThrownBy(budget::markPaid).isInstanceOf(BusinessException.class);
        budget.approve();
        budget.markPaid();
        assertThat(budget.getStatus()).isEqualTo(BudgetStatus.PAID);
        assertThat(budget.canBeCancelled()).isFalse();
    }
}

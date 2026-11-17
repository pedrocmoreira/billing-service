package com.pedrocmoreira.garage.billing;

import com.pedrocmoreira.garage.billing.application.PaymentService;
import com.pedrocmoreira.garage.billing.domain.exception.EntityNotFoundException;
import com.pedrocmoreira.garage.billing.domain.model.*;
import com.pedrocmoreira.garage.billing.domain.port.MessagePublisher;
import com.pedrocmoreira.garage.billing.domain.port.PaymentGateway;
import com.pedrocmoreira.garage.billing.domain.saga.SagaMessage;
import com.pedrocmoreira.garage.billing.infrastructure.persistence.BudgetRepository;
import com.pedrocmoreira.garage.billing.infrastructure.persistence.PaymentRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static com.pedrocmoreira.garage.billing.domain.saga.MessageTypes.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaymentServiceTest {
    private PaymentRepository paymentRepository;
    private BudgetRepository budgetRepository;
    private PaymentGateway gateway;
    private MessagePublisher publisher;
    private PaymentService service;
    private Budget budget;
    private Payment payment;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        budgetRepository = mock(BudgetRepository.class);
        gateway = mock(PaymentGateway.class);
        publisher = mock(MessagePublisher.class);
        service = new PaymentService(paymentRepository, budgetRepository, gateway, publisher, new SimpleMeterRegistry());
        budget = BillingFixtures.budget();
        budget.approve();
        payment = BillingFixtures.payment(budget);
        when(paymentRepository.findByBudgetId(5L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private SagaMessage lastPublished() {
        ArgumentCaptor<SagaMessage> captor = ArgumentCaptor.forClass(SagaMessage.class);
        verify(publisher, atLeastOnce()).publish(captor.capture());
        return captor.getValue();
    }

    private void gatewayReturns(String status, String detail) {
        when(gateway.getPayment("123")).thenReturn(new PaymentGateway.PaymentInfo("123", status, detail, "5", new BigDecimal("302.00")));
    }

    @Test
    void approvedNotificationConfirmsPayment() {
        gatewayReturns("approved", "accredited");
        service.processNotification("123");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getExternalPaymentId()).isEqualTo("123");
        assertThat(budget.getStatus()).isEqualTo(BudgetStatus.PAID);
        assertThat(lastPublished().type()).isEqualTo(EVT_PAYMENT_CONFIRMED);
    }

    @Test
    void rejectedNotificationPublishesFailure() {
        gatewayReturns("rejected", "cc_rejected_insufficient_amount");
        service.processNotification("123");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REJECTED);
        SagaMessage event = lastPublished();
        assertThat(event.type()).isEqualTo(EVT_PAYMENT_FAILED);
        assertThat(event.dataAsString("reason")).isEqualTo("cc_rejected_insufficient_amount");
    }

    @Test
    void cancelledWithoutDetailUsesStatusAsReason() {
        gatewayReturns("cancelled", null);
        service.processNotification("123");
        assertThat(lastPublished().dataAsString("reason")).isEqualTo("cancelled");
    }

    @Test
    void pendingNotificationKeepsWaiting() {
        gatewayReturns("in_process", "pending_review");
        service.processNotification("123");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verifyNoInteractions(publisher);
    }

    @Test
    void duplicatedNotificationIsIgnored() {
        payment.setStatus(PaymentStatus.APPROVED);
        gatewayReturns("approved", "accredited");
        assertThat(service.processNotification("123")).contains(payment);
        verifyNoInteractions(publisher);
    }

    @Test
    void notificationWithoutReferenceIsIgnored() {
        when(gateway.getPayment("9")).thenReturn(new PaymentGateway.PaymentInfo("9", "approved", null, null, null));
        assertThat(service.processNotification("9")).isEmpty();
    }

    @Test
    void notificationForUnknownBudgetFails() {
        when(gateway.getPayment("9")).thenReturn(new PaymentGateway.PaymentInfo("9", "approved", null, "77", null));
        assertThatThrownBy(() -> service.processNotification("9")).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void refundApprovedPayment() {
        payment.setStatus(PaymentStatus.APPROVED);
        payment.setExternalPaymentId("123");
        when(paymentRepository.findByBudgetServiceOrderId(100L)).thenReturn(Optional.of(payment));

        service.refund(SagaMessage.of(CMD_BILLING_REFUND_PAYMENT, 100L, "OS", Map.of("reason", "sem estoque")));

        verify(gateway).refund("123");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(budget.getStatus()).isEqualTo(BudgetStatus.REFUNDED);
        SagaMessage event = lastPublished();
        assertThat(event.type()).isEqualTo(EVT_PAYMENT_REFUNDED);
        assertThat(event.data()).containsEntry("refunded", true);
    }

    @Test
    void refundWithoutApprovedPaymentStillAnswers() {
        when(paymentRepository.findByBudgetServiceOrderId(100L)).thenReturn(Optional.empty());
        service.refund(SagaMessage.of(CMD_BILLING_REFUND_PAYMENT, 100L, "OS", Map.of()));
        verify(gateway, never()).refund(any());
        assertThat(lastPublished().data()).containsEntry("refunded", false);
    }

    @Test
    void findByServiceOrder() {
        when(paymentRepository.findByBudgetServiceOrderId(100L)).thenReturn(Optional.of(payment));
        assertThat(service.findByServiceOrder(100L)).isSameAs(payment);
        assertThatThrownBy(() -> service.findByServiceOrder(1L)).isInstanceOf(EntityNotFoundException.class);
    }
}

package com.pedrocmoreira.garage.billing.application;

import com.pedrocmoreira.garage.billing.domain.exception.EntityNotFoundException;
import com.pedrocmoreira.garage.billing.domain.model.Budget;
import com.pedrocmoreira.garage.billing.domain.model.BudgetStatus;
import com.pedrocmoreira.garage.billing.domain.model.Payment;
import com.pedrocmoreira.garage.billing.domain.model.PaymentStatus;
import com.pedrocmoreira.garage.billing.domain.port.MessagePublisher;
import com.pedrocmoreira.garage.billing.domain.port.PaymentGateway;
import com.pedrocmoreira.garage.billing.domain.saga.SagaMessage;
import com.pedrocmoreira.garage.billing.infrastructure.persistence.BudgetRepository;
import com.pedrocmoreira.garage.billing.infrastructure.persistence.PaymentRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.pedrocmoreira.garage.billing.domain.saga.MessageTypes.*;

/** Registro/verificação de pagamentos (webhook do Mercado Pago) e estorno (compensação). */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final BudgetRepository budgetRepository;
    private final PaymentGateway paymentGateway;
    private final MessagePublisher publisher;
    private final MeterRegistry meterRegistry;

    /**
     * Notificação de pagamento: consulta o status real no Mercado Pago (não confia no corpo do webhook)
     * e publica o evento correspondente para o orquestrador.
     */
    @Transactional
    public Optional<Payment> processNotification(String externalPaymentId) {
        PaymentGateway.PaymentInfo info = paymentGateway.getPayment(externalPaymentId);
        if (info.externalReference() == null) {
            log.warn("Pagamento {} sem external_reference - ignorado", externalPaymentId);
            return Optional.empty();
        }
        Payment payment = paymentRepository.findByBudgetId(Long.valueOf(info.externalReference()))
                .orElseThrow(() -> new EntityNotFoundException("Pagamento do orçamento", info.externalReference()));

        if (payment.getStatus().isFinal()) {
            log.info("Pagamento {} já está {} - notificação duplicada ignorada", payment.getId(), payment.getStatus());
            return Optional.of(payment);
        }

        Budget budget = payment.getBudget();
        payment.setExternalPaymentId(info.paymentId());
        payment.setStatusDetail(info.statusDetail());
        payment.setUpdatedAt(LocalDateTime.now());

        if (info.approved()) {
            payment.setStatus(PaymentStatus.APPROVED);
            budget.markPaid();
            budgetRepository.save(budget);
            publisher.publish(SagaMessage.of(EVT_PAYMENT_CONFIRMED, budget.getServiceOrderId(), budget.getServiceOrderNumber(),
                    Map.of("paymentId", info.paymentId(), "amount", payment.getAmount())));
            meterRegistry.counter("garage.billing.payment", "result", "approved").increment();
        } else if (info.failed()) {
            payment.setStatus(PaymentStatus.REJECTED);
            publisher.publish(SagaMessage.of(EVT_PAYMENT_FAILED, budget.getServiceOrderId(), budget.getServiceOrderNumber(),
                    Map.of("paymentId", info.paymentId(), "reason", info.statusDetail() == null ? info.status() : info.statusDetail())));
            meterRegistry.counter("garage.billing.payment", "result", "rejected").increment();
        } else {
            log.info("Pagamento {} ainda em processamento ({})", info.paymentId(), info.status());
        }
        return Optional.of(paymentRepository.save(payment));
    }

    /** Compensação {@code command.billing.payment.refund}: estorna o pagamento aprovado. */
    @Transactional
    public void refund(SagaMessage command) {
        Optional<Payment> maybePayment = paymentRepository.findByBudgetServiceOrderId(command.serviceOrderId());
        Map<String, Object> data = new HashMap<>();
        data.put("reason", command.dataAsString("reason"));

        maybePayment.filter(p -> p.getStatus() == PaymentStatus.APPROVED).ifPresent(payment -> {
            paymentGateway.refund(payment.getExternalPaymentId());
            payment.setStatus(PaymentStatus.REFUNDED);
            payment.setUpdatedAt(LocalDateTime.now());
            paymentRepository.save(payment);
            Budget budget = payment.getBudget();
            budget.setStatus(BudgetStatus.REFUNDED);
            budgetRepository.save(budget);
            data.put("paymentId", payment.getExternalPaymentId());
            data.put("amount", payment.getAmount());
            meterRegistry.counter("garage.billing.payment.refunded").increment();
        });
        data.put("refunded", data.containsKey("paymentId"));

        publisher.publish(SagaMessage.of(EVT_PAYMENT_REFUNDED, command.serviceOrderId(), command.serviceOrderNumber(), data));
    }

    public Payment findByServiceOrder(Long serviceOrderId) {
        return paymentRepository.findByBudgetServiceOrderId(serviceOrderId)
                .orElseThrow(() -> new EntityNotFoundException("Pagamento da OS", serviceOrderId));
    }
}

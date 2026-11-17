package com.pedrocmoreira.garage.billing.application;

import com.pedrocmoreira.garage.billing.domain.exception.EntityNotFoundException;
import com.pedrocmoreira.garage.billing.domain.model.*;
import com.pedrocmoreira.garage.billing.domain.port.BudgetNotifier;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.pedrocmoreira.garage.billing.domain.saga.MessageTypes.*;

/** Geração, envio, aprovação/recusa e cancelamento (compensação) de orçamentos. */
@Slf4j
@Service
@RequiredArgsConstructor
public class BudgetService {
    private final BudgetRepository budgetRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final BudgetNotifier notifier;
    private final MessagePublisher publisher;
    private final MeterRegistry meterRegistry;

    /** Comando {@code command.billing.budget.create}: idempotente por OS. */
    @Transactional
    public Budget create(SagaMessage command) {
        Optional<Budget> existing = budgetRepository.findByServiceOrderId(command.serviceOrderId());
        if (existing.isPresent()) {
            log.info("Orçamento da OS {} já existe - reenviando confirmação", command.serviceOrderNumber());
            publishCreated(existing.get());
            return existing.get();
        }

        Budget budget = Budget.builder()
                .serviceOrderId(command.serviceOrderId())
                .serviceOrderNumber(command.serviceOrderNumber())
                .customerName(command.dataAsString("customerName"))
                .customerEmail(command.dataAsString("customerEmail"))
                .vehicleDescription(command.dataAsString("vehicleDescription"))
                .diagnosisNotes(command.dataAsString("diagnosisNotes"))
                .build();
        items(command.data().get("services")).forEach(i -> budget.addItem(toItem(i, BudgetItem.ItemType.SERVICE)));
        items(command.data().get("parts")).forEach(i -> budget.addItem(toItem(i, BudgetItem.ItemType.PART)));

        Budget saved = budgetRepository.save(budget);
        notifier.sendBudget(saved);
        publishCreated(saved);
        meterRegistry.counter("garage.billing.budget.created").increment();
        return saved;
    }

    /** Cliente aprova pelo link do e-mail: gera a cobrança no Mercado Pago. */
    @Transactional
    public Payment approve(String token) {
        Budget budget = findByToken(token);
        budget.approve();
        budgetRepository.save(budget);

        PaymentGateway.CheckoutSession checkout = paymentGateway.createCheckout(budget);
        Payment payment = paymentRepository.save(Payment.builder()
                .budget(budget)
                .provider(paymentGateway.providerName())
                .preferenceId(checkout.preferenceId())
                .checkoutUrl(checkout.checkoutUrl())
                .amount(budget.getTotalAmount())
                .build());

        publisher.publish(SagaMessage.of(EVT_BUDGET_APPROVED, budget.getServiceOrderId(), budget.getServiceOrderNumber(),
                Map.of("budgetId", budget.getId(), "paymentUrl", checkout.checkoutUrl())));
        meterRegistry.counter("garage.billing.budget.decision", "decision", "approved").increment();
        return payment;
    }

    /** Cliente recusa pelo link do e-mail: dispara a compensação da saga. */
    @Transactional
    public Budget reject(String token) {
        Budget budget = findByToken(token);
        budget.reject();
        budgetRepository.save(budget);
        publisher.publish(SagaMessage.of(EVT_BUDGET_REJECTED, budget.getServiceOrderId(), budget.getServiceOrderNumber(),
                Map.of("budgetId", budget.getId())));
        meterRegistry.counter("garage.billing.budget.decision", "decision", "rejected").increment();
        return budget;
    }

    /** Compensação {@code command.billing.budget.cancel}. */
    @Transactional
    public void cancel(SagaMessage command) {
        budgetRepository.findByServiceOrderId(command.serviceOrderId()).ifPresentOrElse(budget -> {
            if (budget.canBeCancelled()) {
                budget.setStatus(BudgetStatus.CANCELLED);
                budgetRepository.save(budget);
                paymentRepository.findByBudgetId(budget.getId())
                        .filter(p -> p.getStatus() == PaymentStatus.PENDING)
                        .ifPresent(p -> {
                            p.setStatus(PaymentStatus.CANCELLED);
                            p.setUpdatedAt(LocalDateTime.now());
                            paymentRepository.save(p);
                        });
            }
            publisher.publish(SagaMessage.of(EVT_BUDGET_CANCELLED, command.serviceOrderId(), command.serviceOrderNumber(),
                    Map.of("budgetStatus", budget.getStatus().name())));
        }, () -> log.warn("Cancelamento recebido para OS {} sem orçamento", command.serviceOrderNumber()));
    }

    public Budget findByToken(String token) {
        return budgetRepository.findByPublicToken(token)
                .orElseThrow(() -> new EntityNotFoundException("Orçamento", token));
    }

    public Budget findByServiceOrder(Long serviceOrderId) {
        return budgetRepository.findByServiceOrderId(serviceOrderId)
                .orElseThrow(() -> new EntityNotFoundException("Orçamento da OS", serviceOrderId));
    }

    private void publishCreated(Budget budget) {
        publisher.publish(SagaMessage.of(EVT_BUDGET_CREATED, budget.getServiceOrderId(), budget.getServiceOrderNumber(),
                Map.of("budgetId", budget.getId(), "totalAmount", budget.getTotalAmount())));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(Object raw) {
        return raw instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    private static BudgetItem toItem(Map<String, Object> raw, BudgetItem.ItemType type) {
        Object quantity = raw.getOrDefault("quantity", 1);
        Object price = raw.getOrDefault("unitPrice", 0);
        return BudgetItem.builder()
                .type(type)
                .description(String.valueOf(raw.get("name")))
                .quantity(Integer.parseInt(quantity.toString()))
                .unitPrice(new BigDecimal(price.toString()))
                .build();
    }
}

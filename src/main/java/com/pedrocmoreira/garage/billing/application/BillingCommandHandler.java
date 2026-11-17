package com.pedrocmoreira.garage.billing.application;

import com.pedrocmoreira.garage.billing.domain.saga.SagaMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static com.pedrocmoreira.garage.billing.domain.saga.MessageTypes.*;

/** Roteia os comandos do orquestrador para os casos de uso do Billing. */
@Slf4j
@Component
@RequiredArgsConstructor
public class BillingCommandHandler {
    private final BudgetService budgetService;
    private final PaymentService paymentService;

    public void handle(SagaMessage command) {
        log.info("Comando {} recebido para OS {}", command.type(), command.serviceOrderNumber());
        switch (command.type()) {
            case CMD_BILLING_CREATE_BUDGET -> budgetService.create(command);
            case CMD_BILLING_CANCEL_BUDGET -> budgetService.cancel(command);
            case CMD_BILLING_REFUND_PAYMENT -> paymentService.refund(command);
            default -> log.warn("Comando desconhecido: {}", command.type());
        }
    }
}

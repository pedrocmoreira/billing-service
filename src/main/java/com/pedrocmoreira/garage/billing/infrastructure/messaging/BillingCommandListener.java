package com.pedrocmoreira.garage.billing.infrastructure.messaging;

import com.pedrocmoreira.garage.billing.application.BillingCommandHandler;
import com.pedrocmoreira.garage.billing.domain.saga.MessageTypes;
import com.pedrocmoreira.garage.billing.domain.saga.SagaMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BillingCommandListener {
    private final BillingCommandHandler handler;

    @RabbitListener(queues = MessageTypes.QUEUE_BILLING_COMMANDS)
    public void onCommand(SagaMessage command) {
        handler.handle(command);
    }
}

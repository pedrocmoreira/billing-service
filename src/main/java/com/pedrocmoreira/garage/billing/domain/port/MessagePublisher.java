package com.pedrocmoreira.garage.billing.domain.port;

import com.pedrocmoreira.garage.billing.domain.saga.SagaMessage;

/** Porta de saída para publicação de comandos/eventos na mensageria. */
public interface MessagePublisher {
    void publish(SagaMessage message);
}

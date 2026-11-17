package com.pedrocmoreira.garage.billing.infrastructure.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.pedrocmoreira.garage.billing.domain.saga.MessageTypes.*;

/**
 * Topologia RabbitMQ: exchange topic "garage.saga" (comandos e eventos) e DLX para mensagens com falha.
 * O Billing consome os comandos "command.billing.#" enviados pelo orquestrador.
 */
@Configuration
public class RabbitConfig {

    @Bean
    TopicExchange sagaExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
    }

    @Bean
    TopicExchange deadLetterExchange() {
        return ExchangeBuilder.topicExchange(DEAD_LETTER_EXCHANGE).durable(true).build();
    }

    @Bean
    Queue billingCommandsQueue() {
        return QueueBuilder.durable(QUEUE_BILLING_COMMANDS)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(QUEUE_BILLING_COMMANDS + ".dlq")
                .build();
    }

    @Bean
    Queue billingCommandsDlq() {
        return QueueBuilder.durable(QUEUE_BILLING_COMMANDS + ".dlq").build();
    }

    @Bean
    Binding billingCommandsBinding() {
        return BindingBuilder.bind(billingCommandsQueue()).to(sagaExchange()).with("command.billing.#");
    }

    @Bean
    Binding billingCommandsDlqBinding() {
        return BindingBuilder.bind(billingCommandsDlq()).to(deadLetterExchange()).with(QUEUE_BILLING_COMMANDS + ".dlq");
    }

    @Bean
    MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}

package com.pedrocmoreira.garage.billing.infrastructure.config;

import com.pedrocmoreira.garage.billing.domain.port.PaymentGateway;
import com.pedrocmoreira.garage.billing.infrastructure.payment.MercadoPagoHttpGateway;
import com.pedrocmoreira.garage.billing.infrastructure.payment.WebhookSignatureValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Gateway de pagamento via HTTP. Por padrão aponta para o simulador (repositório payment-mock-service);
 * para usar o Mercado Pago real basta trocar MP_API_BASE_URL e MP_ACCESS_TOKEN no .env.
 */
@Slf4j
@Configuration
public class PaymentGatewayConfig {

    @Bean
    PaymentGateway paymentGateway(RestClient.Builder builder,
                                  @Value("${mercadopago.access-token}") String accessToken,
                                  @Value("${mercadopago.api-base-url}") String apiBaseUrl,
                                  @Value("${mercadopago.use-sandbox-url:true}") boolean useSandboxUrl,
                                  @Value("${mercadopago.notification-url}") String notificationUrl,
                                  @Value("${app.public-base-url}") String publicBaseUrl) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException("MP_ACCESS_TOKEN não configurado no .env");
        }
        log.info("Gateway de pagamento: {} (webhook em {})", apiBaseUrl, notificationUrl);
        return new MercadoPagoHttpGateway(builder, apiBaseUrl, accessToken, publicBaseUrl, notificationUrl, useSandboxUrl);
    }

    @Bean
    WebhookSignatureValidator webhookSignatureValidator(@Value("${mercadopago.webhook-secret:}") String secret) {
        return new WebhookSignatureValidator(secret);
    }
}

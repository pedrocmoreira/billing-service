package com.pedrocmoreira.garage.billing.infrastructure.payment;

import com.pedrocmoreira.garage.billing.domain.model.Budget;
import com.pedrocmoreira.garage.billing.domain.port.PaymentGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Integração HTTP com a API do Mercado Pago (Checkout Pro). A URL base é configurável:
 * aponta para o simulador {@code payment-mock-service} ou para https://api.mercadopago.com.
 * Docs: https://www.mercadopago.com.br/developers/pt/docs/checkout-pro
 * <ul>
 *   <li>POST /checkout/preferences - cria a preferência (link de pagamento)</li>
 *   <li>GET /v1/payments/{id} - consulta o pagamento notificado via webhook</li>
 *   <li>POST /v1/payments/{id}/refunds - estorno (compensação da saga)</li>
 * </ul>
 */
@Slf4j
public class MercadoPagoHttpGateway implements PaymentGateway {
    private final RestClient client;
    private final String publicBaseUrl;
    private final String notificationUrl;
    private final boolean useSandboxUrl;

    /**
     * @param publicBaseUrl   URL do Billing vista pelo navegador do cliente (back_urls)
     * @param notificationUrl URL do webhook vista pelo provedor de pagamento (notification_url)
     */
    public MercadoPagoHttpGateway(RestClient.Builder builder, String apiBaseUrl, String accessToken,
                                  String publicBaseUrl, String notificationUrl, boolean useSandboxUrl) {
        this.client = builder.baseUrl(apiBaseUrl)
                .defaultHeader("Authorization", "Bearer " + accessToken)
                .build();
        this.publicBaseUrl = publicBaseUrl;
        this.notificationUrl = notificationUrl;
        this.useSandboxUrl = useSandboxUrl;
    }

    @Override
    public String providerName() {
        return "MERCADO_PAGO";
    }

    @Override
    @SuppressWarnings("unchecked")
    public CheckoutSession createCheckout(Budget budget) {
        Map<String, Object> body = Map.of(
                "external_reference", String.valueOf(budget.getId()),
                "notification_url", notificationUrl,
                "statement_descriptor", "GARAGE SYSTEM",
                "items", List.of(Map.of(
                        "id", budget.getServiceOrderNumber(),
                        "title", "Ordem de serviço " + budget.getServiceOrderNumber(),
                        "quantity", 1,
                        "currency_id", "BRL",
                        "unit_price", budget.getTotalAmount())),
                "back_urls", Map.of(
                        "success", publicBaseUrl + "/api/public/budgets/" + budget.getPublicToken() + "/payment-return",
                        "failure", publicBaseUrl + "/api/public/budgets/" + budget.getPublicToken() + "/payment-return",
                        "pending", publicBaseUrl + "/api/public/budgets/" + budget.getPublicToken() + "/payment-return"));

        Map<String, Object> response = client.post().uri("/checkout/preferences")
                .header("X-Idempotency-Key", "pref-" + budget.getId())
                .body(body)
                .retrieve()
                .body(Map.class);
        if (response == null) {
            throw new IllegalStateException("Resposta vazia do Mercado Pago ao criar preferência");
        }
        String url = (String) (useSandboxUrl && response.get("sandbox_init_point") != null
                ? response.get("sandbox_init_point") : response.get("init_point"));
        log.info("Preferência Mercado Pago {} criada para OS {}", response.get("id"), budget.getServiceOrderNumber());
        return new CheckoutSession(String.valueOf(response.get("id")), url);
    }

    @Override
    @SuppressWarnings("unchecked")
    public PaymentInfo getPayment(String paymentId) {
        Map<String, Object> p = client.get().uri("/v1/payments/{id}", paymentId).retrieve().body(Map.class);
        if (p == null) {
            throw new IllegalStateException("Pagamento " + paymentId + " não encontrado no Mercado Pago");
        }
        Object amount = p.get("transaction_amount");
        return new PaymentInfo(String.valueOf(p.get("id")), (String) p.get("status"), (String) p.get("status_detail"),
                (String) p.get("external_reference"), amount == null ? null : new BigDecimal(amount.toString()));
    }

    @Override
    public void refund(String paymentId) {
        client.post().uri("/v1/payments/{id}/refunds", paymentId)
                .header("X-Idempotency-Key", UUID.nameUUIDFromBytes(("refund-" + paymentId).getBytes()).toString())
                .body(Map.of())
                .retrieve()
                .toBodilessEntity();
        log.info("Estorno solicitado no Mercado Pago para o pagamento {}", paymentId);
    }
}

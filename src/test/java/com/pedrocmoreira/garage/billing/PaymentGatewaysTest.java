package com.pedrocmoreira.garage.billing;

import com.pedrocmoreira.garage.billing.domain.model.Budget;
import com.pedrocmoreira.garage.billing.domain.port.PaymentGateway;
import com.pedrocmoreira.garage.billing.infrastructure.payment.MercadoPagoHttpGateway;
import com.pedrocmoreira.garage.billing.infrastructure.payment.WebhookSignatureValidator;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PaymentGatewaysTest {

    @Test
    void mercadoPagoGatewayCallsRealApiContract() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MercadoPagoHttpGateway gateway = new MercadoPagoHttpGateway(builder, "https://api.mercadopago.com", "TEST-token", "https://garage.test", "https://garage.test/webhooks/mercadopago", true);
        Budget budget = BillingFixtures.budget();

        server.expect(requestTo("https://api.mercadopago.com/checkout/preferences"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer TEST-token"))
                .andExpect(jsonPath("$.external_reference").value("5"))
                .andExpect(jsonPath("$.notification_url").value("https://garage.test/webhooks/mercadopago"))
                .andRespond(withSuccess("{\"id\":\"pref-1\",\"init_point\":\"https://mp/init\",\"sandbox_init_point\":\"https://mp/sandbox\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.mercadopago.com/v1/payments/123"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":123,\"status\":\"approved\",\"status_detail\":\"accredited\",\"external_reference\":\"5\",\"transaction_amount\":302.0}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.mercadopago.com/v1/payments/123/refunds"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        PaymentGateway.CheckoutSession session = gateway.createCheckout(budget);
        assertThat(session.preferenceId()).isEqualTo("pref-1");
        assertThat(session.checkoutUrl()).isEqualTo("https://mp/sandbox");

        PaymentGateway.PaymentInfo info = gateway.getPayment("123");
        assertThat(info.approved()).isTrue();
        assertThat(info.externalReference()).isEqualTo("5");
        assertThat(info.amount()).isEqualByComparingTo("302.0");

        gateway.refund("123");
        assertThat(gateway.providerName()).isEqualTo("MERCADO_PAGO");
        server.verify();
    }

    @Test
    void mercadoPagoGatewayUsesProductionUrlWhenSandboxDisabled() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MercadoPagoHttpGateway gateway = new MercadoPagoHttpGateway(builder, "http://payment-mock-service:8084", "APP_USR", "https://g", "http://billing:8082/webhooks/mercadopago", false);
        server.expect(requestTo("http://payment-mock-service:8084/checkout/preferences"))
                .andExpect(jsonPath("$.notification_url").value("http://billing:8082/webhooks/mercadopago"))
                .andRespond(withSuccess("{\"id\":\"p\",\"init_point\":\"https://mp/init\"}", MediaType.APPLICATION_JSON));
        assertThat(gateway.createCheckout(BillingFixtures.budget()).checkoutUrl()).isEqualTo("https://mp/init");
    }


    @Test
    void webhookSignatureValidation() throws Exception {
        WebhookSignatureValidator disabled = new WebhookSignatureValidator("");
        assertThat(disabled.isEnabled()).isFalse();
        assertThat(disabled.isValid(null, null, null)).isTrue();

        String secret = "segredo";
        WebhookSignatureValidator validator = new WebhookSignatureValidator(secret);
        String manifest = "id:123;request-id:req-1;ts:1700000000;";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String v1 = HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));

        assertThat(validator.isValid("ts=1700000000,v1=" + v1, "req-1", "123")).isTrue();
        assertThat(validator.isValid("ts=1700000000,v1=abc", "req-1", "123")).isFalse();
        assertThat(validator.isValid("ts=1700000000", "req-1", "123")).isFalse();
        assertThat(validator.isValid(null, "req-1", "123")).isFalse();
        assertThat(validator.isValid("ts=1,v1=x", null, "123")).isFalse();
    }
}

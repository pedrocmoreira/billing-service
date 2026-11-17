package com.pedrocmoreira.garage.billing.domain.port;

import com.pedrocmoreira.garage.billing.domain.model.Budget;

import java.math.BigDecimal;

/** Porta de integração com o provedor de pagamentos (Mercado Pago). */
public interface PaymentGateway {

    record CheckoutSession(String preferenceId, String checkoutUrl) {}

    /**
     * @param status status do Mercado Pago: approved, rejected, cancelled, pending, in_process, refunded...
     * @param externalReference referência enviada na criação da preferência (id do orçamento)
     */
    record PaymentInfo(String paymentId, String status, String statusDetail, String externalReference, BigDecimal amount) {
        public boolean approved() { return "approved".equalsIgnoreCase(status); }
        public boolean failed() { return "rejected".equalsIgnoreCase(status) || "cancelled".equalsIgnoreCase(status); }
    }

    String providerName();

    CheckoutSession createCheckout(Budget budget);

    PaymentInfo getPayment(String paymentId);

    void refund(String paymentId);
}

package com.pedrocmoreira.garage.billing.presentation.controller;

import com.pedrocmoreira.garage.billing.application.PaymentService;
import com.pedrocmoreira.garage.billing.infrastructure.payment.WebhookSignatureValidator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Webhook do Mercado Pago. Aceita os dois formatos de notificação:
 * query (?type=payment&data.id=123 / ?topic=payment&id=123) e corpo JSON {"type":"payment","data":{"id":"123"}}.
 */
@Slf4j
@RestController
@RequestMapping("/webhooks/mercadopago")
@RequiredArgsConstructor
@Tag(name = "Webhook Mercado Pago")
public class MercadoPagoWebhookController {
    private final PaymentService paymentService;
    private final WebhookSignatureValidator signatureValidator;

    @PostMapping
    @Operation(summary = "Recebe notificações de pagamento do Mercado Pago")
    public ResponseEntity<Void> receive(@RequestParam Map<String, String> params,
                                        @RequestBody(required = false) Map<String, Object> body,
                                        @RequestHeader(value = "x-signature", required = false) String signature,
                                        @RequestHeader(value = "x-request-id", required = false) String requestId) {
        String type = firstNonNull(params.get("type"), params.get("topic"), body == null ? null : (String) body.get("type"));
        String dataId = firstNonNull(params.get("data.id"), params.get("id"), extractBodyId(body));

        if (!"payment".equals(type) || dataId == null) {
            log.info("Notificação Mercado Pago ignorada (type={}, id={})", type, dataId);
            return ResponseEntity.ok().build();
        }
        if (!signatureValidator.isValid(signature, requestId, dataId)) {
            log.warn("Assinatura inválida no webhook do Mercado Pago (id={})", dataId);
            return ResponseEntity.status(401).build();
        }
        paymentService.processNotification(dataId);
        return ResponseEntity.ok().build();
    }

    private static String extractBodyId(Map<String, Object> body) {
        if (body != null && body.get("data") instanceof Map<?, ?> data && data.get("id") != null) {
            return data.get("id").toString();
        }
        return null;
    }

    private static String firstNonNull(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }
}

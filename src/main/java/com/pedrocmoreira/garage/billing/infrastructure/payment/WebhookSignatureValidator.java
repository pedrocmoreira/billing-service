package com.pedrocmoreira.garage.billing.infrastructure.payment;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Valida o header x-signature dos webhooks do Mercado Pago:
 * manifest = "id:{data.id};request-id:{x-request-id};ts:{ts};" assinado com HMAC-SHA256 usando a chave secreta.
 * Se MP_WEBHOOK_SECRET não estiver configurado, a validação é ignorada (ambiente de desenvolvimento).
 */
public class WebhookSignatureValidator {
    private final String secret;

    public WebhookSignatureValidator(String secret) {
        this.secret = secret;
    }

    public boolean isEnabled() {
        return secret != null && !secret.isBlank();
    }

    public boolean isValid(String signatureHeader, String requestId, String dataId) {
        if (!isEnabled()) {
            return true;
        }
        if (signatureHeader == null || dataId == null) {
            return false;
        }
        String ts = null;
        String v1 = null;
        for (String part : signatureHeader.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2 && kv[0].equals("ts")) ts = kv[1];
            if (kv.length == 2 && kv[0].equals("v1")) v1 = kv[1];
        }
        if (ts == null || v1 == null) {
            return false;
        }
        StringBuilder manifest = new StringBuilder("id:").append(dataId.toLowerCase()).append(';');
        if (requestId != null && !requestId.isBlank()) {
            manifest.append("request-id:").append(requestId).append(';');
        }
        manifest.append("ts:").append(ts).append(';');
        return MessageDigest.isEqual(hmac(manifest.toString()).getBytes(StandardCharsets.UTF_8), v1.getBytes(StandardCharsets.UTF_8));
    }

    String hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao calcular HMAC", e);
        }
    }
}

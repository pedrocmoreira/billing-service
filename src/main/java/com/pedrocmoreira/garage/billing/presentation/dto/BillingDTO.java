package com.pedrocmoreira.garage.billing.presentation.dto;

import com.pedrocmoreira.garage.billing.domain.model.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class BillingDTO {
    private BillingDTO() {}

    public record ItemResponse(String type, String description, Integer quantity, BigDecimal unitPrice, BigDecimal subtotal) {
        static ItemResponse from(BudgetItem i) {
            return new ItemResponse(i.getType().name(), i.getDescription(), i.getQuantity(), i.getUnitPrice(), i.getSubtotal());
        }
    }

    /** {@code publicToken} identifica os links de aprovação/recusa (visível apenas na consulta autenticada e no próprio link). */
    public record BudgetResponse(Long id, Long serviceOrderId, String serviceOrderNumber, String publicToken, String customerName,
                                 String vehicleDescription, BudgetStatus status, BigDecimal totalAmount,
                                 LocalDateTime createdAt, LocalDateTime decidedAt, List<ItemResponse> items) {
        public static BudgetResponse from(Budget b) {
            return new BudgetResponse(b.getId(), b.getServiceOrderId(), b.getServiceOrderNumber(), b.getPublicToken(), b.getCustomerName(),
                    b.getVehicleDescription(), b.getStatus(), b.getTotalAmount(), b.getCreatedAt(), b.getDecidedAt(),
                    b.getItems().stream().map(ItemResponse::from).toList());
        }
    }

    public record PaymentResponse(Long id, Long budgetId, String provider, String preferenceId, String checkoutUrl,
                                  String externalPaymentId, PaymentStatus status, String statusDetail,
                                  BigDecimal amount, LocalDateTime createdAt, LocalDateTime updatedAt) {
        public static PaymentResponse from(Payment p) {
            return new PaymentResponse(p.getId(), p.getBudget().getId(), p.getProvider(), p.getPreferenceId(), p.getCheckoutUrl(),
                    p.getExternalPaymentId(), p.getStatus(), p.getStatusDetail(), p.getAmount(), p.getCreatedAt(), p.getUpdatedAt());
        }
    }
}

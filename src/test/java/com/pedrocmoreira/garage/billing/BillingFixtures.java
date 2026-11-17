package com.pedrocmoreira.garage.billing;

import com.pedrocmoreira.garage.billing.domain.model.Budget;
import com.pedrocmoreira.garage.billing.domain.model.BudgetItem;
import com.pedrocmoreira.garage.billing.domain.model.Payment;
import com.pedrocmoreira.garage.billing.domain.saga.MessageTypes;
import com.pedrocmoreira.garage.billing.domain.saga.SagaMessage;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class BillingFixtures {
    private BillingFixtures() {}

    public static Budget budget() {
        Budget b = Budget.builder().id(5L).serviceOrderId(100L).serviceOrderNumber("OS-2026-00001")
                .customerName("João").customerEmail("joao@email.com").vehicleDescription("Fiat Uno 2020 (ABC1D23)")
                .publicToken("token-123").build();
        b.addItem(BudgetItem.builder().type(BudgetItem.ItemType.SERVICE).description("Troca de óleo").quantity(1).unitPrice(new BigDecimal("150.00")).build());
        b.addItem(BudgetItem.builder().type(BudgetItem.ItemType.PART).description("Óleo").quantity(4).unitPrice(new BigDecimal("38.00")).build());
        return b;
    }

    public static Payment payment(Budget budget) {
        return Payment.builder().id(9L).budget(budget).provider("MERCADO_PAGO").preferenceId("pref")
                .checkoutUrl("http://checkout").amount(budget.getTotalAmount()).build();
    }

    public static SagaMessage createBudgetCommand() {
        return SagaMessage.of(MessageTypes.CMD_BILLING_CREATE_BUDGET, 100L, "OS-2026-00001", Map.of(
                "customerName", "João",
                "customerEmail", "joao@email.com",
                "vehicleDescription", "Fiat Uno",
                "diagnosisNotes", "Óleo vencido",
                "services", List.of(Map.of("serviceId", 1, "name", "Troca de óleo", "quantity", 1, "unitPrice", 150.0)),
                "parts", List.of(Map.of("partId", 2, "name", "Óleo", "quantity", 4, "unitPrice", "38.00"))));
    }
}

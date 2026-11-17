package com.pedrocmoreira.garage.billing.domain.port;

import com.pedrocmoreira.garage.billing.domain.model.Budget;

/** Envio do orçamento ao cliente (e-mail). */
public interface BudgetNotifier {
    void sendBudget(Budget budget);
}

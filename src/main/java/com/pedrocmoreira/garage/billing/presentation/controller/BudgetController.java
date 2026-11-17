package com.pedrocmoreira.garage.billing.presentation.controller;

import com.pedrocmoreira.garage.billing.application.BudgetService;
import com.pedrocmoreira.garage.billing.application.PaymentService;
import com.pedrocmoreira.garage.billing.infrastructure.persistence.BudgetRepository;
import com.pedrocmoreira.garage.billing.presentation.dto.BillingDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Orçamentos e Pagamentos", description = "Consulta interna (requer JWT)")
public class BudgetController {
    private final BudgetRepository budgetRepository;
    private final BudgetService budgetService;
    private final PaymentService paymentService;

    @GetMapping("/budgets")
    @Operation(summary = "Listar orçamentos")
    public List<BillingDTO.BudgetResponse> list() {
        return budgetRepository.findAll().stream().map(BillingDTO.BudgetResponse::from).toList();
    }

    @GetMapping("/budgets/service-order/{serviceOrderId}")
    @Operation(summary = "Orçamento de uma OS")
    public BillingDTO.BudgetResponse byServiceOrder(@PathVariable Long serviceOrderId) {
        return BillingDTO.BudgetResponse.from(budgetService.findByServiceOrder(serviceOrderId));
    }

    @GetMapping("/payments/service-order/{serviceOrderId}")
    @Operation(summary = "Pagamento de uma OS")
    public BillingDTO.PaymentResponse paymentByServiceOrder(@PathVariable Long serviceOrderId) {
        return BillingDTO.PaymentResponse.from(paymentService.findByServiceOrder(serviceOrderId));
    }
}

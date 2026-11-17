package com.pedrocmoreira.garage.billing.presentation.controller;

import com.pedrocmoreira.garage.billing.application.BudgetService;
import com.pedrocmoreira.garage.billing.domain.exception.BusinessException;
import com.pedrocmoreira.garage.billing.domain.model.Budget;
import com.pedrocmoreira.garage.billing.domain.model.Payment;
import com.pedrocmoreira.garage.billing.presentation.dto.BillingDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** Links enviados por e-mail ao cliente (sem autenticação; protegidos por token aleatório). */
@RestController
@RequestMapping("/api/public/budgets")
@RequiredArgsConstructor
@Tag(name = "Orçamento público", description = "Aprovação/recusa pelo cliente via link do e-mail")
public class PublicBudgetController {
    private final BudgetService budgetService;

    @GetMapping("/{token}")
    @Operation(summary = "Visualizar orçamento")
    public BillingDTO.BudgetResponse view(@PathVariable String token) {
        return BillingDTO.BudgetResponse.from(budgetService.findByToken(token));
    }

    @GetMapping(value = "/{token}/approve", produces = "text/html;charset=UTF-8")
    @Operation(summary = "Cliente aprova o orçamento e recebe o link de pagamento do Mercado Pago")
    public String approve(@PathVariable String token) {
        try {
            Payment payment = budgetService.approve(token);
            Budget budget = payment.getBudget();
            return HtmlPages.page("Orçamento aprovado!",
                    "Recebemos sua aprovação para a <strong>OS " + HtmlPages.escape(budget.getServiceOrderNumber())
                            + "</strong>. Valor: <strong>R$ " + budget.getTotalAmount() + "</strong>.<br/>Conclua o pagamento para iniciarmos o serviço.",
                    "#2F9E44",
                    "<p><a class=\"btn pay\" href=\"" + HtmlPages.escape(payment.getCheckoutUrl()) + "\">Pagar com Mercado Pago</a></p>");
        } catch (BusinessException e) {
            return HtmlPages.page("Ação não permitida", HtmlPages.escape(e.getMessage()), "#E67700", "");
        }
    }

    @GetMapping(value = "/{token}/reject", produces = "text/html;charset=UTF-8")
    @Operation(summary = "Cliente recusa o orçamento (a OS é cancelada via compensação da Saga)")
    public String reject(@PathVariable String token) {
        try {
            Budget budget = budgetService.reject(token);
            return HtmlPages.page("Orçamento recusado",
                    "Registramos a recusa do orçamento da <strong>OS " + HtmlPages.escape(budget.getServiceOrderNumber())
                            + "</strong>. A ordem de serviço será cancelada.", "#E03131", "");
        } catch (BusinessException e) {
            return HtmlPages.page("Ação não permitida", HtmlPages.escape(e.getMessage()), "#E67700", "");
        }
    }

    @GetMapping(value = "/{token}/payment-return", produces = "text/html;charset=UTF-8")
    @Operation(summary = "Página de retorno do checkout do Mercado Pago")
    public String paymentReturn(@PathVariable String token, @RequestParam(required = false) String status) {
        Budget budget = budgetService.findByToken(token);
        return HtmlPages.page("Pagamento em processamento",
                "Recebemos o retorno do Mercado Pago (" + HtmlPages.escape(status) + ") para a OS "
                        + HtmlPages.escape(budget.getServiceOrderNumber()) + ". A confirmação chega via notificação.", "#3B5BDB", "");
    }
}

package com.pedrocmoreira.garage.billing;

import com.pedrocmoreira.garage.billing.application.BudgetService;
import com.pedrocmoreira.garage.billing.application.PaymentService;
import com.pedrocmoreira.garage.billing.domain.exception.BusinessException;
import com.pedrocmoreira.garage.billing.domain.model.Budget;
import com.pedrocmoreira.garage.billing.domain.model.Payment;
import com.pedrocmoreira.garage.billing.infrastructure.payment.WebhookSignatureValidator;
import com.pedrocmoreira.garage.billing.infrastructure.persistence.BudgetRepository;
import com.pedrocmoreira.garage.billing.presentation.controller.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ControllersTest {
    private BudgetService budgetService;
    private PaymentService paymentService;
    private BudgetRepository budgetRepository;
    private WebhookSignatureValidator validator;
    private Budget budget;
    private Payment payment;

    @BeforeEach
    void setUp() {
        budgetService = mock(BudgetService.class);
        paymentService = mock(PaymentService.class);
        budgetRepository = mock(BudgetRepository.class);
        validator = mock(WebhookSignatureValidator.class);
        budget = BillingFixtures.budget();
        payment = BillingFixtures.payment(budget);
    }

    private MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(
                        new BudgetController(budgetRepository, budgetService, paymentService),
                        new PublicBudgetController(budgetService),
                        new MercadoPagoWebhookController(paymentService, validator))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void internalQueries() throws Exception {
        MockMvc mvc = mvc();
        when(budgetRepository.findAll()).thenReturn(List.of(budget));
        when(budgetService.findByServiceOrder(100L)).thenReturn(budget);
        when(paymentService.findByServiceOrder(100L)).thenReturn(payment);

        mvc.perform(get("/api/budgets")).andExpect(status().isOk()).andExpect(jsonPath("$[0].totalAmount").value(302.0));
        mvc.perform(get("/api/budgets/service-order/100")).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(2));
        mvc.perform(get("/api/payments/service-order/100")).andExpect(status().isOk()).andExpect(jsonPath("$.checkoutUrl").value("http://checkout"));
    }

    @Test
    void publicApproveRejectPages() throws Exception {
        MockMvc mvc = mvc();
        when(budgetService.findByToken("token-123")).thenReturn(budget);
        when(budgetService.approve("token-123")).thenReturn(payment);
        when(budgetService.reject("token-123")).thenReturn(budget);

        mvc.perform(get("/api/public/budgets/token-123")).andExpect(status().isOk());
        mvc.perform(get("/api/public/budgets/token-123/approve"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Pagar com Mercado Pago")));
        mvc.perform(get("/api/public/budgets/token-123/reject"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("recusado")));
        mvc.perform(get("/api/public/budgets/token-123/payment-return?status=approved"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("approved")));

        when(budgetService.approve("token-123")).thenThrow(new BusinessException("já aprovado"));
        when(budgetService.reject("token-123")).thenThrow(new BusinessException("já aprovado"));
        mvc.perform(get("/api/public/budgets/token-123/approve")).andExpect(content().string(containsString("não permitida")));
        mvc.perform(get("/api/public/budgets/token-123/reject")).andExpect(content().string(containsString("não permitida")));
    }

    @Test
    void webhookAcceptsQueryAndBodyFormats() throws Exception {
        MockMvc mvc = mvc();
        when(validator.isValid(any(), any(), any())).thenReturn(true);

        mvc.perform(post("/webhooks/mercadopago?type=payment&data.id=123")).andExpect(status().isOk());
        mvc.perform(post("/webhooks/mercadopago").contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"payment\",\"data\":{\"id\":\"456\"}}")).andExpect(status().isOk());
        mvc.perform(post("/webhooks/mercadopago?topic=merchant_order&id=1")).andExpect(status().isOk());
        mvc.perform(post("/webhooks/mercadopago").contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"payment\"}"))
                .andExpect(status().isOk());

        verify(paymentService).processNotification("123");
        verify(paymentService).processNotification("456");
        verify(paymentService, times(2)).processNotification(anyString());

        when(validator.isValid(any(), any(), any())).thenReturn(false);
        mvc.perform(post("/webhooks/mercadopago?type=payment&data.id=789")).andExpect(status().isUnauthorized());
    }



    @Test
    void errorsAreMapped() throws Exception {
        MockMvc mvc = mvc();
        when(budgetRepository.findAll()).thenThrow(new IllegalStateException("x"));
        mvc.perform(get("/api/budgets")).andExpect(status().isInternalServerError());
        when(budgetService.findByServiceOrder(1L)).thenThrow(new IllegalArgumentException("bad"));
        mvc.perform(get("/api/budgets/service-order/1")).andExpect(status().isBadRequest());
        when(budgetService.findByServiceOrder(2L)).thenThrow(new BusinessException("regra"));
        mvc.perform(get("/api/budgets/service-order/2")).andExpect(status().is(422));
    }
}

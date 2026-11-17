package com.pedrocmoreira.garage.billing;

import com.pedrocmoreira.garage.billing.application.BillingCommandHandler;
import com.pedrocmoreira.garage.billing.application.BudgetService;
import com.pedrocmoreira.garage.billing.application.PaymentService;
import com.pedrocmoreira.garage.billing.domain.model.Budget;
import com.pedrocmoreira.garage.billing.domain.saga.MessageTypes;
import com.pedrocmoreira.garage.billing.domain.saga.SagaMessage;
import com.pedrocmoreira.garage.billing.infrastructure.mail.EmailBudgetNotifier;
import com.pedrocmoreira.garage.billing.infrastructure.messaging.BillingCommandListener;
import com.pedrocmoreira.garage.billing.infrastructure.messaging.RabbitMessagePublisher;
import com.pedrocmoreira.garage.billing.infrastructure.security.JwtValidationFilter;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.IContext;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InfrastructureTest {
    private static final String SECRET = "segredo-de-teste-com-mais-de-32-caracteres!!";

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void commandHandlerRoutesCommands() {
        BudgetService budgetService = mock(BudgetService.class);
        PaymentService paymentService = mock(PaymentService.class);
        BillingCommandHandler handler = new BillingCommandHandler(budgetService, paymentService);
        BillingCommandListener listener = new BillingCommandListener(handler);

        SagaMessage create = SagaMessage.of(MessageTypes.CMD_BILLING_CREATE_BUDGET, 1L, "OS", Map.of());
        SagaMessage cancel = SagaMessage.of(MessageTypes.CMD_BILLING_CANCEL_BUDGET, 1L, "OS", Map.of());
        SagaMessage refund = SagaMessage.of(MessageTypes.CMD_BILLING_REFUND_PAYMENT, 1L, "OS", Map.of());
        listener.onCommand(create);
        handler.handle(cancel);
        handler.handle(refund);
        handler.handle(SagaMessage.of("command.billing.unknown", 1L, "OS", Map.of()));

        verify(budgetService).create(create);
        verify(budgetService).cancel(cancel);
        verify(paymentService).refund(refund);
    }

    @Test
    void publisherSendsAfterCommit() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        RabbitMessagePublisher publisher = new RabbitMessagePublisher(template);
        SagaMessage msg = SagaMessage.of(MessageTypes.EVT_BUDGET_CREATED, 1L, "OS", Map.of());

        publisher.publish(msg);
        verify(template, times(1)).convertAndSend(eq(MessageTypes.EXCHANGE), eq(msg.type()), eq(msg), any(MessagePostProcessor.class));

        TransactionSynchronizationManager.initSynchronization();
        publisher.publish(msg);
        verify(template, times(1)).convertAndSend(eq(MessageTypes.EXCHANGE), eq(msg.type()), eq(msg), any(MessagePostProcessor.class));
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(template, times(2)).convertAndSend(eq(MessageTypes.EXCHANGE), eq(msg.type()), eq(msg), any(MessagePostProcessor.class));
    }

    @Test
    void emailNotifierRendersTemplateAndSends() {
        JavaMailSender sender = mock(JavaMailSender.class);
        TemplateEngine engine = mock(TemplateEngine.class);
        MimeMessage mime = new MimeMessage((jakarta.mail.Session) null);
        when(sender.createMimeMessage()).thenReturn(mime);
        when(engine.process(eq("email/budget"), any(IContext.class))).thenReturn("<html>ok</html>");
        EmailBudgetNotifier notifier = new EmailBudgetNotifier(sender, engine);
        ReflectionTestUtils.setField(notifier, "from", "noreply@garage.com");
        ReflectionTestUtils.setField(notifier, "publicBaseUrl", "http://localhost:8082");

        notifier.sendBudget(BillingFixtures.budget());
        verify(sender).send(mime);

        Budget noEmail = BillingFixtures.budget();
        noEmail.setCustomerEmail(" ");
        notifier.sendBudget(noEmail);
        verify(sender, times(1)).send(any(MimeMessage.class));

        doThrow(new RuntimeException("smtp down")).when(sender).send(any(MimeMessage.class));
        notifier.sendBudget(BillingFixtures.budget()); // não propaga a falha
    }

    @Test
    void jwtFilterAuthenticatesTokenFromOsService() throws Exception {
        JwtValidationFilter filter = new JwtValidationFilter(SECRET);
        String token = Jwts.builder().subject("admin").claim("role", "ROLE_ADMIN")
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);

        filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("admin");

        SecurityContextHolder.clearContext();
        String noRole = Jwts.builder().subject("x").signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
        MockHttpServletRequest r2 = new MockHttpServletRequest();
        r2.addHeader("Authorization", "Bearer " + noRole);
        filter.doFilter(r2, new MockHttpServletResponse(), mock(FilterChain.class));
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities()).isEmpty();

        SecurityContextHolder.clearContext();
        MockHttpServletRequest invalid = new MockHttpServletRequest();
        invalid.addHeader("Authorization", "Bearer invalido");
        filter.doFilter(invalid, new MockHttpServletResponse(), mock(FilterChain.class));
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), mock(FilterChain.class));
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}

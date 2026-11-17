package com.pedrocmoreira.garage.billing.infrastructure.mail;

import com.pedrocmoreira.garage.billing.domain.model.Budget;
import com.pedrocmoreira.garage.billing.domain.model.BudgetItem;
import com.pedrocmoreira.garage.billing.domain.port.BudgetNotifier;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Locale;

/** Envia o orçamento ao cliente com os links de aprovação/recusa. Falhas de e-mail não quebram a saga. */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailBudgetNotifier implements BudgetNotifier {
    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${app.mail.from}")
    private String from;

    @Value("${app.public-base-url}")
    private String publicBaseUrl;

    @Override
    public void sendBudget(Budget budget) {
        if (budget.getCustomerEmail() == null || budget.getCustomerEmail().isBlank()) {
            log.warn("Cliente da OS {} sem e-mail - orçamento disponível apenas pelo link público", budget.getServiceOrderNumber());
            return;
        }
        try {
            Context ctx = new Context(Locale.forLanguageTag("pt-BR"));
            ctx.setVariable("customerName", budget.getCustomerName());
            ctx.setVariable("serviceOrderNumber", budget.getServiceOrderNumber());
            ctx.setVariable("vehicleDescription", budget.getVehicleDescription());
            ctx.setVariable("diagnosisNotes", budget.getDiagnosisNotes());
            ctx.setVariable("services", budget.getItems().stream().filter(i -> i.getType() == BudgetItem.ItemType.SERVICE).toList());
            ctx.setVariable("parts", budget.getItems().stream().filter(i -> i.getType() == BudgetItem.ItemType.PART).toList());
            ctx.setVariable("totalAmount", budget.getTotalAmount());
            ctx.setVariable("approveUrl", publicBaseUrl + "/api/public/budgets/" + budget.getPublicToken() + "/approve");
            ctx.setVariable("rejectUrl", publicBaseUrl + "/api/public/budgets/" + budget.getPublicToken() + "/reject");
            String html = templateEngine.process("email/budget", ctx);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(budget.getCustomerEmail());
            helper.setSubject("Orçamento " + budget.getServiceOrderNumber() + " - Garage System");
            helper.setText(html, true);
            mailSender.send(message);
            log.info("Orçamento da OS {} enviado por e-mail", budget.getServiceOrderNumber());
        } catch (Exception e) {
            log.error("Falha ao enviar e-mail do orçamento da OS {}: {}", budget.getServiceOrderNumber(), e.getMessage());
        }
    }
}

package com.pedrocmoreira.garage.billing.domain.model;

import com.pedrocmoreira.garage.billing.domain.exception.BusinessException;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Orçamento gerado a partir do diagnóstico, enviado ao cliente para aprovação. */
@Entity
@Table(name = "budgets")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Budget {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long serviceOrderId;

    @Column(nullable = false)
    private String serviceOrderNumber;

    /** Token aleatório usado nos links públicos de aprovação/recusa enviados por e-mail. */
    @Column(nullable = false, unique = true, length = 36)
    @Builder.Default
    private String publicToken = UUID.randomUUID().toString();

    @Column(nullable = false)
    private String customerName;

    private String customerEmail;

    private String vehicleDescription;

    @Column(columnDefinition = "TEXT")
    private String diagnosisNotes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private BudgetStatus status = BudgetStatus.PENDING_APPROVAL;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime decidedAt;

    @OneToMany(mappedBy = "budget", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<BudgetItem> items = new ArrayList<>();

    public void addItem(BudgetItem item) {
        item.setBudget(this);
        items.add(item);
        totalAmount = items.stream().map(BudgetItem::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void approve() {
        requireStatus(BudgetStatus.PENDING_APPROVAL);
        status = BudgetStatus.APPROVED;
        decidedAt = LocalDateTime.now();
    }

    public void reject() {
        requireStatus(BudgetStatus.PENDING_APPROVAL);
        status = BudgetStatus.REJECTED;
        decidedAt = LocalDateTime.now();
    }

    public void markPaid() {
        requireStatus(BudgetStatus.APPROVED);
        status = BudgetStatus.PAID;
    }

    public boolean canBeCancelled() {
        return status == BudgetStatus.PENDING_APPROVAL || status == BudgetStatus.APPROVED;
    }

    private void requireStatus(BudgetStatus expected) {
        if (status != expected) {
            throw new BusinessException("Orçamento da OS " + serviceOrderNumber + " está " + status + " (esperado " + expected + ")");
        }
    }
}

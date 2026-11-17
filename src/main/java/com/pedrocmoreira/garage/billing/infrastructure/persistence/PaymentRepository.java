package com.pedrocmoreira.garage.billing.infrastructure.persistence;

import com.pedrocmoreira.garage.billing.domain.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByBudgetId(Long budgetId);
    Optional<Payment> findByBudgetServiceOrderId(Long serviceOrderId);
}

package com.pedrocmoreira.garage.billing.infrastructure.persistence;

import com.pedrocmoreira.garage.billing.domain.model.Budget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BudgetRepository extends JpaRepository<Budget, Long> {
    Optional<Budget> findByServiceOrderId(Long serviceOrderId);
    Optional<Budget> findByPublicToken(String publicToken);
}

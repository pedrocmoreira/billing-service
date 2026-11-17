package com.pedrocmoreira.garage.billing.domain.model;

public enum PaymentStatus {
    PENDING, APPROVED, REJECTED, CANCELLED, REFUNDED;

    public boolean isFinal() {
        return this != PENDING;
    }
}

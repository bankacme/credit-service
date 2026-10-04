package com.bank.credit.domain.event;

import com.bank.credit.domain.model.Credit;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Estado completo de un crédito, el payload común de credit.created/updated/closed. */
public record CreditState(
        String creditId, String customerId, String ownerType, String status, BigDecimal principalAmount,
        BigDecimal outstandingBalance, LocalDate dueDate, Instant updatedAt) {

    public static CreditState of(Credit credit) {
        return new CreditState(credit.id().value(), credit.customerId(), credit.ownerType().name(),
                credit.status().name(), credit.principalAmount().amount(), credit.outstandingBalance().amount(),
                credit.dueDate(), credit.updatedAt());
    }
}

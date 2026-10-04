package com.bank.credit.domain.model;

import java.time.LocalDate;

/** Resultado inmutable de un consumo de tarjeta. */
public record ChargeResult(
        OperationId operationId,
        String cardId,
        Money amount,
        Money usedAmount,
        Money availableCredit,
        LocalDate paymentDueDate,
        CardStatus status) {

    public ChargeResult {
        if (operationId == null || cardId == null || amount == null || usedAmount == null || availableCredit == null
                || paymentDueDate == null || status == null) {
            throw new IllegalArgumentException("Required ChargeResult fields must not be null");
        }
    }
}

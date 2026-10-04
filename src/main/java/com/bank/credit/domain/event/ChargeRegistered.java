package com.bank.credit.domain.event;

import com.bank.credit.domain.model.CreditOperation;
import java.math.BigDecimal;
import java.time.Instant;

/** credit.card.charge.registered (kafka-contract 6.8). {@code resultingBalance} = monto usado. */
public record ChargeRegistered(
        String operationId, String cardId, String customerId, BigDecimal amount, BigDecimal resultingBalance,
        BigDecimal availableCredit, String description, Instant occurredAt) implements CreditDomainEvent {

    public static ChargeRegistered from(CreditOperation operation) {
        return new ChargeRegistered(operation.operationId().value(), operation.productId(), operation.customerId(),
                operation.amount().amount(), operation.resultingBalance().amount(),
                operation.availableCredit().amount(), operation.description(), operation.occurredAt());
    }
}

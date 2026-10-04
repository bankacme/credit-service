package com.bank.credit.domain.event;

import com.bank.credit.domain.model.CreditOperation;
import java.math.BigDecimal;
import java.time.Instant;

/** credit.payment.registered (kafka-contract 6.8). {@code customerId} = dueño del producto. */
public record PaymentRegistered(
        String operationId, String productType, String productId, String customerId, String payerCustomerId,
        BigDecimal amount, BigDecimal resultingBalance, Instant occurredAt) implements CreditDomainEvent {

    public static PaymentRegistered from(CreditOperation operation) {
        return new PaymentRegistered(operation.operationId().value(), operation.productType().name(),
                operation.productId(), operation.customerId(), operation.payerCustomerId(),
                operation.amount().amount(), operation.resultingBalance().amount(), operation.occurredAt());
    }
}

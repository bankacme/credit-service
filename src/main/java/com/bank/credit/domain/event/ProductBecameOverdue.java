package com.bank.credit.domain.event;

import com.bank.credit.domain.model.Credit;
import com.bank.credit.domain.model.CreditCard;
import com.bank.credit.domain.model.ProductType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** credit.overdue.detected: un aviso por producto que pasa a vencido (kafka-contract 6.7). */
public record ProductBecameOverdue(
        String customerId, String productType, String productId, LocalDate dueDate, BigDecimal outstandingAmount,
        Instant occurredAt) implements CreditDomainEvent {

    public static ProductBecameOverdue from(Credit credit) {
        return new ProductBecameOverdue(credit.customerId(), ProductType.CREDIT.name(), credit.id().value(),
                credit.dueDate(), credit.outstandingBalance().amount(), credit.updatedAt());
    }

    public static ProductBecameOverdue from(CreditCard card) {
        return new ProductBecameOverdue(card.customerId(), ProductType.CREDIT_CARD.name(), card.id().value(),
                card.paymentDueDate(), card.usedAmount().amount(), card.updatedAt());
    }
}

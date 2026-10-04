package com.bank.credit.domain.event;

import com.bank.credit.domain.model.CreditCard;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Estado completo de una tarjeta (sin el número completo: solo enmascarado). */
public record CreditCardState(
        String cardId, String customerId, String ownerType, String maskedNumber, String status,
        BigDecimal creditLimit, BigDecimal usedAmount, LocalDate paymentDueDate, Instant updatedAt) {

    public static CreditCardState of(CreditCard card) {
        return new CreditCardState(card.id().value(), card.customerId(), card.ownerType().name(),
                card.cardNumber().masked(), card.status().name(), card.creditLimit().amount(),
                card.usedAmount().amount(), card.paymentDueDate(), card.updatedAt());
    }
}

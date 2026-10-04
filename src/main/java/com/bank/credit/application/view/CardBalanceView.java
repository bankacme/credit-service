package com.bank.credit.application.view;

import com.bank.credit.domain.model.CardStatus;
import com.bank.credit.domain.model.CreditCard;
import com.bank.credit.domain.model.Money;
import java.time.Instant;
import java.time.LocalDate;

/** RF-31: línea, usado, disponible, fecha de pago y estado de una tarjeta a un instante dado. */
public record CardBalanceView(
        String cardId, CardStatus status, Money creditLimit, Money usedAmount, Money availableCredit,
        LocalDate paymentDueDate, Instant asOf) {

    public static CardBalanceView of(CreditCard card, Instant asOf) {
        return new CardBalanceView(card.id().value(), card.status(), card.creditLimit(), card.usedAmount(),
                card.availableCredit(), card.paymentDueDate(), asOf);
    }
}

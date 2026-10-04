package com.bank.credit.domain.event;

import com.bank.credit.domain.model.CreditCard;

public record CreditCardClosed(CreditCardState state) implements CreditDomainEvent {

    public static CreditCardClosed from(CreditCard card) {
        return new CreditCardClosed(CreditCardState.of(card));
    }
}

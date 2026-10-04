package com.bank.credit.domain.event;

import com.bank.credit.domain.model.CreditCard;

public record CreditCardIssued(CreditCardState state) implements CreditDomainEvent {

    public static CreditCardIssued from(CreditCard card) {
        return new CreditCardIssued(CreditCardState.of(card));
    }
}

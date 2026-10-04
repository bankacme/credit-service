package com.bank.credit.domain.event;

import com.bank.credit.domain.model.CreditCard;

public record CreditCardUpdated(CreditCardState state) implements CreditDomainEvent {

    public static CreditCardUpdated from(CreditCard card) {
        return new CreditCardUpdated(CreditCardState.of(card));
    }
}

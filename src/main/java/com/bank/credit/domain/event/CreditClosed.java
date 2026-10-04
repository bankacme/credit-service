package com.bank.credit.domain.event;

import com.bank.credit.domain.model.Credit;

public record CreditClosed(CreditState state) implements CreditDomainEvent {

    public static CreditClosed from(Credit credit) {
        return new CreditClosed(CreditState.of(credit));
    }
}

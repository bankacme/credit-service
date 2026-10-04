package com.bank.credit.domain.event;

import com.bank.credit.domain.model.Credit;

public record CreditUpdated(CreditState state) implements CreditDomainEvent {

    public static CreditUpdated from(Credit credit) {
        return new CreditUpdated(CreditState.of(credit));
    }
}

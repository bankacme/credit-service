package com.bank.credit.domain.event;

import com.bank.credit.domain.model.Credit;

public record CreditOpened(CreditState state) implements CreditDomainEvent {

    public static CreditOpened from(Credit credit) {
        return new CreditOpened(CreditState.of(credit));
    }
}

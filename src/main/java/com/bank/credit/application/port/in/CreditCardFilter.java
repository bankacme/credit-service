package com.bank.credit.application.port.in;

import com.bank.credit.domain.model.CardStatus;

/** Filtros opcionales de GET /credit-cards (null = sin filtro). */
public record CreditCardFilter(String customerId, CardStatus status) {

    public static CreditCardFilter all() {
        return new CreditCardFilter(null, null);
    }
}

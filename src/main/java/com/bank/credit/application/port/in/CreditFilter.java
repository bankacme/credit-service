package com.bank.credit.application.port.in;

import com.bank.credit.domain.model.CreditStatus;
import com.bank.credit.domain.model.OwnerType;

/** Filtros opcionales de GET /credits (null = sin filtro). */
public record CreditFilter(String customerId, OwnerType ownerType, CreditStatus status) {

    public static CreditFilter all() {
        return new CreditFilter(null, null, null);
    }
}

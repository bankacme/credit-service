package com.bank.credit.domain.model;

import java.util.UUID;

public record CreditCardId(String value) {

    public CreditCardId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("CreditCardId must not be blank");
        }
    }

    public static CreditCardId newId() {
        return new CreditCardId(UUID.randomUUID().toString());
    }
}

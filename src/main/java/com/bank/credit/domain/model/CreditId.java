package com.bank.credit.domain.model;

import java.util.UUID;

public record CreditId(String value) {

    public CreditId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("CreditId must not be blank");
        }
    }

    public static CreditId newId() {
        return new CreditId(UUID.randomUUID().toString());
    }
}

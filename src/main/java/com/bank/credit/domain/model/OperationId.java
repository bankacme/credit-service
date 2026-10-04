package com.bank.credit.domain.model;

/** Identificador de idempotencia que manda el cliente (8-64 caracteres, lo valida el contrato). */
public record OperationId(String value) {

    public OperationId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("OperationId must not be blank");
        }
    }
}

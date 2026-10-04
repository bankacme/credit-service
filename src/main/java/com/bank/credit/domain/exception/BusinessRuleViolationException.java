package com.bank.credit.domain.exception;

/** Regla de negocio incumplida. {@code errorCode} es el código del contrato (422, o 409 para algunos). */
public class BusinessRuleViolationException extends RuntimeException {

    private final String errorCode;

    public BusinessRuleViolationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}

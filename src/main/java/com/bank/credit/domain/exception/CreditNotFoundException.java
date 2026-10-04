package com.bank.credit.domain.exception;

public class CreditNotFoundException extends RuntimeException {

    public CreditNotFoundException(String creditId) {
        super("Credit not found: " + creditId);
    }
}

package com.bank.credit.domain.exception;

public class CreditCardNotFoundException extends RuntimeException {

    public CreditCardNotFoundException(String cardId) {
        super("Credit card not found: " + cardId);
    }
}

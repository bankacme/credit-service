package com.bank.credit.domain.exception;

/** El número de tarjeta generado ya existe (índice único uk_card_number): el caso de uso genera otro. */
public class CardNumberCollisionException extends RuntimeException {

    public CardNumberCollisionException() {
        super("Generated card number already exists");
    }
}

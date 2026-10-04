package com.bank.credit.domain.model;

/**
 * Número ficticio de 16 dígitos (prefijo de pruebas 400000 + dígito de control Luhn, ver
 * {@code CardNumberGenerator}). Nunca sale completo por REST ni por eventos: solo {@link #masked()}.
 */
public record CardNumber(String value) {

    private static final int LENGTH = 16;
    private static final int VISIBLE_DIGITS = 4;

    public CardNumber {
        if (value == null || value.length() != LENGTH || !value.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException("CardNumber must have exactly 16 digits");
        }
    }

    public String masked() {
        return "**** " + value.substring(LENGTH - VISIBLE_DIGITS);
    }

    /** toString enmascarado: el número completo no debe terminar en un log por accidente. */
    @Override
    public String toString() {
        return masked();
    }
}

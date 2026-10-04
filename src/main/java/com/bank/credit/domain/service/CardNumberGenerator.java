package com.bank.credit.domain.service;

import com.bank.credit.domain.model.CardNumber;
import java.util.random.RandomGenerator;

/**
 * Número de tarjeta ficticio: prefijo de pruebas {@code 400000}, 9 dígitos al azar y el dígito de
 * control Luhn (data-model 1.3). El random se inyecta para poder fijarlo en las pruebas; la
 * unicidad la garantiza el índice {@code uk_card_number} (el caso de uso reintenta hasta 3 veces).
 */
public class CardNumberGenerator {

    private static final String TEST_PREFIX = "400000";
    private static final int RANDOM_DIGITS = 9;

    public CardNumber generate(RandomGenerator random) {
        StringBuilder digits = new StringBuilder(TEST_PREFIX);
        for (int i = 0; i < RANDOM_DIGITS; i++) {
            digits.append(random.nextInt(10));
        }
        digits.append(luhnCheckDigit(digits));
        return new CardNumber(digits.toString());
    }

    /** Dígito que hace que el número completo pase la validación Luhn. */
    static int luhnCheckDigit(CharSequence payload) {
        int sum = 0;
        boolean doubleIt = true;
        for (int i = payload.length() - 1; i >= 0; i--) {
            int digit = payload.charAt(i) - '0';
            if (doubleIt) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubleIt = !doubleIt;
        }
        return (10 - sum % 10) % 10;
    }

    /** Validación Luhn del número completo (usada por las pruebas). */
    static boolean isLuhnValid(String number) {
        return luhnCheckDigit(number.substring(0, number.length() - 1)) == number.charAt(number.length() - 1) - '0';
    }
}

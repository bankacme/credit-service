package com.bank.credit.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.credit.domain.model.CardNumber;
import java.util.Random;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class CardNumberGeneratorTest {

    private final CardNumberGenerator generator = new CardNumberGenerator();

    @RepeatedTest(20)
    void generatesATestRangeNumberThatPassesLuhn() {
        CardNumber number = generator.generate(new Random());

        assertThat(number.value()).hasSize(16).startsWith("400000");
        assertThat(CardNumberGenerator.isLuhnValid(number.value())).isTrue();
    }

    @Test
    void computesTheKnownLuhnCheckDigit() {
        // 4111 1111 1111 1111 es el número de prueba Luhn clásico.
        assertThat(CardNumberGenerator.luhnCheckDigit("411111111111111")).isEqualTo(1);
        assertThat(CardNumberGenerator.isLuhnValid("4111111111111111")).isTrue();
        assertThat(CardNumberGenerator.isLuhnValid("4111111111111112")).isFalse();
    }

    @Test
    void isDeterministicForTheSameSeed() {
        assertThat(generator.generate(new Random(42))).isEqualTo(generator.generate(new Random(42)));
    }
}

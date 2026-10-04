package com.bank.credit.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void roundsToTwoDecimalsHalfEven() {
        assertThat(Money.of(new BigDecimal("10.125")).amount()).isEqualByComparingTo("10.12");
        assertThat(Money.of(new BigDecimal("10.135")).amount()).isEqualByComparingTo("10.14");
    }

    @Test
    void rejectsNegativeAmountsAndOtherCurrencies() {
        assertThatThrownBy(() -> Money.of("-0.01")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Money(BigDecimal.ONE, "USD")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addsSubtractsAndCompares() {
        Money hundred = Money.of("100.00");
        assertThat(hundred.plus(Money.of("50.50")).amount()).isEqualByComparingTo("150.50");
        assertThat(hundred.minus(Money.of("100.00")).isZero()).isTrue();
        assertThat(hundred.isGreaterThan(Money.of("99.99"))).isTrue();
        assertThat(hundred.isGreaterThan(Money.of("100.00"))).isFalse();
    }

    @Test
    void cannotGoBelowZero() {
        assertThatThrownBy(() -> Money.of("10.00").minus(Money.of("10.01")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equalityIgnoresTheScaleItWasCreatedWith() {
        assertThat(Money.of("5")).isEqualTo(Money.of("5.00"));
    }
}

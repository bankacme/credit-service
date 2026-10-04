package com.bank.credit.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CardNumberTest {

    @Test
    void requiresSixteenDigits() {
        assertThatThrownBy(() -> new CardNumber("400000123")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CardNumber("40000012345678AB")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void onlyShowsTheLastFourDigits() {
        CardNumber number = new CardNumber("4000001234564821");

        assertThat(number.masked()).isEqualTo("**** 4821");
        assertThat(number.toString()).isEqualTo("**** 4821").doesNotContain("400000");
    }
}

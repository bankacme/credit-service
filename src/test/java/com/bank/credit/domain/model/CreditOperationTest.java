package com.bank.credit.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class CreditOperationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 1);
    private final Clock clock = Clock.fixed(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
    private final Instant now = clock.instant();

    @Test
    void aPaymentOperationGivesBackTheSameResultAndStartsUnrecorded() {
        Credit credit = Credit.open("cust-A", OwnerType.PERSONAL, Money.of("5000.00"), TODAY.plusDays(90), false,
                clock);
        PaymentResult result = credit.registerPayment(new OperationId("op-00001"), Money.of("300.00"), "cust-B",
                clock).result();

        CreditOperation operation = CreditOperation.ofPayment(result, credit.customerId(), now);

        assertThat(operation.recorded()).isFalse();
        assertThat(operation.customerId()).isEqualTo("cust-A");
        assertThat(operation.toPaymentResult()).isEqualTo(result);
        assertThatThrownBy(operation::toChargeResult).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aChargeOperationGivesBackTheSameResult() {
        CreditCard card = CreditCard.issue("cust-A", OwnerType.PERSONAL, new CardNumber("4000001234564821"),
                Money.of("2000.00"), clock);
        ChargeResult result = card.charge(new OperationId("op-00002"), Money.of("500.00"), 30, clock).result();

        CreditOperation operation = CreditOperation.ofCharge(result, card.customerId(), "Supermercado", now);

        assertThat(operation.toChargeResult()).isEqualTo(result);
        assertThat(operation.description()).isEqualTo("Supermercado");
    }

    @Test
    void onlyMatchesTheSameProductTypeAndAmount() {
        Credit credit = Credit.open("cust-A", OwnerType.PERSONAL, Money.of("5000.00"), TODAY.plusDays(90), false,
                clock);
        PaymentResult result = credit.registerPayment(new OperationId("op-00001"), Money.of("300.00"), null, clock)
                .result();
        CreditOperation operation = CreditOperation.ofPayment(result, "cust-A", now);
        String creditId = credit.id().value();

        assertThat(operation.matches(creditId, OperationType.PAYMENT, Money.of("300.00"))).isTrue();
        assertThat(operation.matches(creditId, OperationType.PAYMENT, Money.of("300.01"))).isFalse();
        assertThat(operation.matches("other", OperationType.PAYMENT, Money.of("300.00"))).isFalse();
        assertThat(operation.matches(creditId, OperationType.CHARGE, Money.of("300.00"))).isFalse();
    }

    @Test
    void markRecordedKeepsEverythingElse() {
        Credit credit = Credit.open("cust-A", OwnerType.PERSONAL, Money.of("5000.00"), TODAY.plusDays(90), false,
                clock);
        CreditOperation operation = CreditOperation.ofPayment(
                credit.registerPayment(new OperationId("op-00001"), Money.of("1.00"), null, clock).result(),
                "cust-A", now);

        CreditOperation recorded = operation.markRecorded(now.plusSeconds(5));

        assertThat(recorded.recorded()).isTrue();
        assertThat(recorded.recordedAt()).isEqualTo(now.plusSeconds(5));
        assertThat(recorded.toPaymentResult()).isEqualTo(operation.toPaymentResult());
    }
}

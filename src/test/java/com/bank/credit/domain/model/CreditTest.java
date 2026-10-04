package com.bank.credit.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bank.credit.domain.exception.BusinessRuleViolationException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class CreditTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 1);
    private final Clock clock = Clock.fixed(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
    private final OperationId op = new OperationId("op-00001");

    private Credit openCredit(String amount) {
        return Credit.open("cust-A", OwnerType.PERSONAL, Money.of(amount), TODAY.plusDays(90), false, clock);
    }

    private static String codeOf(Throwable e) {
        return ((BusinessRuleViolationException) e).getErrorCode();
    }

    @Test
    void opensActiveWithTheWholeAmountOutstanding() {
        Credit credit = openCredit("5000.00");

        assertThat(credit.status()).isEqualTo(CreditStatus.ACTIVE);
        assertThat(credit.outstandingBalance()).isEqualTo(Money.of("5000.00"));
        assertThat(credit.principalAmount()).isEqualTo(credit.outstandingBalance());
        assertThat(credit.version()).isZero();
        assertThat(credit.isUnpaid()).isTrue();
    }

    @Test
    void rejectsADueDateThatIsNotInTheFutureUnlessInDemoMode() {
        assertThatThrownBy(() -> Credit.open("cust-A", OwnerType.PERSONAL, Money.of("100.00"), TODAY, false, clock))
                .isInstanceOf(BusinessRuleViolationException.class)
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo("INVALID_DUE_DATE"));

        Credit pastDue = Credit.open("cust-C", OwnerType.PERSONAL, Money.of("800.00"), TODAY.minusDays(10), true,
                clock);
        assertThat(pastDue.dueDate()).isEqualTo(TODAY.minusDays(10));
    }

    @Test
    void rejectsAZeroAmount() {
        assertThatThrownBy(() -> openCredit("0.00")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aPartialPaymentLowersTheBalanceAndKeepsTheStatus() {
        Credit.PaymentOutcome outcome = openCredit("5000.00")
                .registerPayment(op, Money.of("300.00"), null, clock);

        assertThat(outcome.credit().outstandingBalance()).isEqualTo(Money.of("4700.00"));
        assertThat(outcome.credit().status()).isEqualTo(CreditStatus.ACTIVE);
        assertThat(outcome.result().resultingBalance()).isEqualTo(Money.of("4700.00"));
        assertThat(outcome.result().status()).isEqualTo("ACTIVE");
        assertThat(outcome.result().productType()).isEqualTo(ProductType.CREDIT);
        assertThat(outcome.leftOverdue()).isFalse();
    }

    @Test
    void aFullPaymentMarksItPaid() {
        Credit.PaymentOutcome outcome = openCredit("500.00").registerPayment(op, Money.of("500.00"), null, clock);

        assertThat(outcome.credit().status()).isEqualTo(CreditStatus.PAID);
        assertThat(outcome.credit().outstandingBalance().isZero()).isTrue();
        assertThat(outcome.credit().isUnpaid()).isFalse();
    }

    @Test
    void rejectsAnOverpayment() {
        assertThatThrownBy(() -> openCredit("500.00").registerPayment(op, Money.of("500.01"), null, clock))
                .isInstanceOf(BusinessRuleViolationException.class)
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo("OVERPAYMENT"));
    }

    @Test
    void rejectsPaymentsOnAPaidCredit() {
        Credit paid = openCredit("500.00").registerPayment(op, Money.of("500.00"), null, clock).credit();

        assertThatThrownBy(() -> paid.registerPayment(new OperationId("op-00002"), Money.of("1.00"), null, clock))
                .isInstanceOf(BusinessRuleViolationException.class)
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo("INVALID_STATE"));
    }

    @Test
    void onlyKeepsThePayerWhenItIsAThirdParty() {
        Credit credit = openCredit("5000.00");

        assertThat(credit.registerPayment(op, Money.of("1.00"), "cust-B", clock).result().payerCustomerId())
                .isEqualTo("cust-B");
        assertThat(credit.registerPayment(op, Money.of("1.00"), "cust-A", clock).result().payerCustomerId())
                .isNull();
        assertThat(credit.registerPayment(op, Money.of("1.00"), null, clock).result().payerCustomerId()).isNull();
    }

    @Test
    void becomesOverdueOnlyWhenTheDueDateHasPassedWithBalance() {
        Credit credit = openCredit("1000.00");

        assertThat(credit.markOverdueIfDue(credit.dueDate(), clock)).isEmpty();
        assertThat(credit.markOverdueIfDue(credit.dueDate().plusDays(1), clock))
                .get().extracting(Credit::status).isEqualTo(CreditStatus.OVERDUE);
    }

    @Test
    void markingOverdueIsIdempotentAndIgnoresPaidCredits() {
        Credit overdue = openCredit("1000.00").markOverdueIfDue(TODAY.plusDays(91), clock).orElseThrow();
        Credit paid = openCredit("1000.00").registerPayment(op, Money.of("1000.00"), null, clock).credit();

        assertThat(overdue.markOverdueIfDue(TODAY.plusDays(92), clock)).isEmpty();
        assertThat(paid.markOverdueIfDue(TODAY.plusDays(92), clock)).isEmpty();
    }

    @Test
    void payingAnOverdueCreditInFullLeavesOverdue() {
        Credit overdue = openCredit("1000.00").markOverdueIfDue(TODAY.plusDays(91), clock).orElseThrow();

        Credit.PaymentOutcome partial = overdue.registerPayment(op, Money.of("400.00"), null, clock);
        assertThat(partial.credit().status()).isEqualTo(CreditStatus.OVERDUE);
        assertThat(partial.leftOverdue()).isFalse();

        Credit.PaymentOutcome full = partial.credit()
                .registerPayment(new OperationId("op-00002"), Money.of("600.00"), null, clock);
        assertThat(full.credit().status()).isEqualTo(CreditStatus.PAID);
        assertThat(full.leftOverdue()).isTrue();
    }

    @Test
    void reschedulingAnOverdueCreditToTheFutureReactivatesIt() {
        Credit overdue = openCredit("1000.00").markOverdueIfDue(TODAY.plusDays(91), clock).orElseThrow();

        Credit rescheduled = overdue.reschedule(TODAY.plusDays(30), false, clock);

        assertThat(rescheduled.status()).isEqualTo(CreditStatus.ACTIVE);
        assertThat(rescheduled.dueDate()).isEqualTo(TODAY.plusDays(30));
    }

    @Test
    void inDemoModeReschedulingToThePastKeepsTheStatus() {
        Credit overdue = openCredit("1000.00").markOverdueIfDue(TODAY.plusDays(91), clock).orElseThrow();

        assertThat(overdue.reschedule(TODAY.minusDays(5), true, clock).status()).isEqualTo(CreditStatus.OVERDUE);
        assertThatThrownBy(() -> overdue.reschedule(TODAY, false, clock))
                .isInstanceOf(BusinessRuleViolationException.class)
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo("INVALID_DUE_DATE"));
    }

    @Test
    void cannotRescheduleAPaidCredit() {
        Credit paid = openCredit("10.00").registerPayment(op, Money.of("10.00"), null, clock).credit();

        assertThatThrownBy(() -> paid.reschedule(TODAY.plusDays(10), false, clock))
                .isInstanceOf(BusinessRuleViolationException.class)
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo("INVALID_STATE"));
    }

    @Test
    void closesOnlyWhenPaidAndClosingTwiceChangesNothing() {
        Credit active = openCredit("10.00");
        assertThatThrownBy(() -> active.close(clock))
                .isInstanceOf(BusinessRuleViolationException.class)
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo("NOT_CLOSABLE"));

        Credit closed = active.registerPayment(op, Money.of("10.00"), null, clock).credit().close(clock);
        assertThat(closed.status()).isEqualTo(CreditStatus.CLOSED);
        assertThat(closed.close(clock)).isSameAs(closed);
    }
}

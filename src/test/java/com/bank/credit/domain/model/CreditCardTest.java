package com.bank.credit.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bank.credit.domain.exception.BusinessRuleViolationException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class CreditCardTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 1);
    private static final int TERM_DAYS = 30;
    private final Clock clock = clockOn(TODAY);
    private final CardNumber number = new CardNumber("4000001234564821");

    private static Clock clockOn(LocalDate date) {
        return Clock.fixed(date.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
    }

    private static OperationId op(int n) {
        return new OperationId(String.format("op-%05d", n));
    }

    private static String codeOf(Throwable e) {
        return ((BusinessRuleViolationException) e).getErrorCode();
    }

    private CreditCard issueCard(String limit) {
        return CreditCard.issue("cust-A", OwnerType.PERSONAL, number, Money.of(limit), clock);
    }

    @Test
    void issuesActiveWithNothingUsed() {
        CreditCard card = issueCard("2000.00");

        assertThat(card.status()).isEqualTo(CardStatus.ACTIVE);
        assertThat(card.usedAmount().isZero()).isTrue();
        assertThat(card.availableCredit()).isEqualTo(Money.of("2000.00"));
        assertThat(card.paymentDueDate()).isNull();
    }

    @Test
    void theFirstChargeSetsThePaymentDueDateAndLaterOnesDoNotMoveIt() {
        CreditCard.ChargeOutcome first = issueCard("2000.00").charge(op(1), Money.of("500.00"), TERM_DAYS, clock);

        assertThat(first.card().usedAmount()).isEqualTo(Money.of("500.00"));
        assertThat(first.card().paymentDueDate()).isEqualTo(TODAY.plusDays(TERM_DAYS));
        assertThat(first.result().availableCredit()).isEqualTo(Money.of("1500.00"));

        CreditCard.ChargeOutcome second = first.card()
                .charge(op(2), Money.of("100.00"), TERM_DAYS, clockOn(TODAY.plusDays(10)));
        assertThat(second.card().paymentDueDate()).isEqualTo(TODAY.plusDays(TERM_DAYS));
        assertThat(second.result().usedAmount()).isEqualTo(Money.of("600.00"));
    }

    @Test
    void canSpendExactlyTheWholeLineButNotMore() {
        CreditCard card = issueCard("2000.00");

        assertThat(card.charge(op(1), Money.of("2000.00"), TERM_DAYS, clock).card().availableCredit().isZero())
                .isTrue();
        assertThatThrownBy(() -> card.charge(op(2), Money.of("2000.01"), TERM_DAYS, clock))
                .isInstanceOf(BusinessRuleViolationException.class)
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo("CREDIT_LIMIT_EXCEEDED"));
    }

    @Test
    void aPartialPaymentKeepsTheDueDateAndAFullOneClearsIt() {
        CreditCard charged = issueCard("2000.00").charge(op(1), Money.of("500.00"), TERM_DAYS, clock).card();

        CreditCard.PaymentOutcome partial = charged.registerPayment(op(2), Money.of("200.00"), null, clock);
        assertThat(partial.card().usedAmount()).isEqualTo(Money.of("300.00"));
        assertThat(partial.card().paymentDueDate()).isEqualTo(TODAY.plusDays(TERM_DAYS));
        assertThat(partial.result().resultingBalance()).isEqualTo(Money.of("300.00"));

        CreditCard.PaymentOutcome full = partial.card().registerPayment(op(3), Money.of("300.00"), null, clock);
        assertThat(full.card().usedAmount().isZero()).isTrue();
        assertThat(full.card().paymentDueDate()).isNull();
        assertThat(full.card().status()).isEqualTo(CardStatus.ACTIVE);
    }

    @Test
    void rejectsPayingMoreThanWhatIsUsed() {
        CreditCard charged = issueCard("2000.00").charge(op(1), Money.of("100.00"), TERM_DAYS, clock).card();

        assertThatThrownBy(() -> charged.registerPayment(op(2), Money.of("100.01"), null, clock))
                .isInstanceOf(BusinessRuleViolationException.class)
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo("OVERPAYMENT"));
        assertThatThrownBy(() -> issueCard("2000.00").registerPayment(op(3), Money.of("1.00"), null, clock))
                .isInstanceOf(BusinessRuleViolationException.class)
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo("OVERPAYMENT"));
    }

    @Test
    void becomesOverdueAfterThePaymentDueDateAndAFullPaymentReactivatesIt() {
        CreditCard charged = issueCard("2000.00").charge(op(1), Money.of("500.00"), TERM_DAYS, clock).card();
        LocalDate afterDue = TODAY.plusDays(TERM_DAYS + 1);

        assertThat(charged.markOverdueIfDue(TODAY.plusDays(TERM_DAYS), clock)).isEmpty();
        CreditCard overdue = charged.markOverdueIfDue(afterDue, clock).orElseThrow();
        assertThat(overdue.status()).isEqualTo(CardStatus.OVERDUE);
        assertThat(overdue.markOverdueIfDue(afterDue, clock)).isEmpty();

        CreditCard.PaymentOutcome paid = overdue.registerPayment(op(2), Money.of("500.00"), "cust-B", clock);
        assertThat(paid.card().status()).isEqualTo(CardStatus.ACTIVE);
        assertThat(paid.leftOverdue()).isTrue();
        assertThat(paid.result().payerCustomerId()).isEqualTo("cust-B");
    }

    @Test
    void aCardWithNothingUsedNeverBecomesOverdue() {
        assertThat(issueCard("2000.00").markOverdueIfDue(TODAY.plusYears(1), clock)).isEmpty();
    }

    @Test
    void anOverdueCardCanStillBeCharged() {
        CreditCard overdue = issueCard("2000.00").charge(op(1), Money.of("500.00"), TERM_DAYS, clock).card()
                .markOverdueIfDue(TODAY.plusDays(TERM_DAYS + 1), clock).orElseThrow();

        CreditCard.ChargeOutcome charged = overdue.charge(op(2), Money.of("100.00"), TERM_DAYS, clock);

        assertThat(charged.card().status()).isEqualTo(CardStatus.OVERDUE);
        assertThat(charged.result().status()).isEqualTo(CardStatus.OVERDUE);
    }

    @Test
    void theLimitCannotGoBelowWhatIsAlreadyUsed() {
        CreditCard charged = issueCard("2000.00").charge(op(1), Money.of("800.00"), TERM_DAYS, clock).card();

        assertThat(charged.changeLimit(Money.of("800.00"), clock).availableCredit().isZero()).isTrue();
        assertThatThrownBy(() -> charged.changeLimit(Money.of("799.99"), clock))
                .isInstanceOf(BusinessRuleViolationException.class)
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo("LIMIT_BELOW_USED_AMOUNT"));
    }

    @Test
    void closesOnlyWithNothingUsedAndThenRejectsEverything() {
        CreditCard charged = issueCard("2000.00").charge(op(1), Money.of("1.00"), TERM_DAYS, clock).card();
        assertThatThrownBy(() -> charged.close(clock))
                .isInstanceOf(BusinessRuleViolationException.class)
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo("NOT_CLOSABLE"));

        CreditCard closed = issueCard("2000.00").close(clock);
        assertThat(closed.status()).isEqualTo(CardStatus.CLOSED);
        assertThat(closed.close(clock)).isSameAs(closed);
        assertThatThrownBy(() -> closed.charge(op(2), Money.of("1.00"), TERM_DAYS, clock))
                .isInstanceOf(BusinessRuleViolationException.class)
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo("INVALID_STATE"));
        assertThatThrownBy(() -> closed.changeLimit(Money.of("10.00"), clock))
                .isInstanceOf(BusinessRuleViolationException.class)
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo("INVALID_STATE"));
    }
}

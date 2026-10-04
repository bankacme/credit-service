package com.bank.credit.application.usecase;

import static com.bank.credit.application.usecase.CreditFixture.TODAY;
import static org.assertj.core.api.Assertions.assertThat;

import com.bank.credit.application.command.PaymentCommand;
import com.bank.credit.domain.event.CreditUpdated;
import com.bank.credit.domain.event.CustomerOverdueCleared;
import com.bank.credit.domain.event.PaymentRegistered;
import com.bank.credit.domain.exception.BusinessRuleViolationException;
import com.bank.credit.domain.exception.CreditNotFoundException;
import com.bank.credit.domain.model.Credit;
import com.bank.credit.domain.model.CreditStatus;
import com.bank.credit.domain.model.Money;
import com.bank.credit.domain.model.OperationId;
import com.bank.credit.domain.model.OwnerType;
import com.bank.credit.domain.model.PaymentResult;
import org.junit.jupiter.api.Test;

class PayCreditUseCaseImplTest {

    private final CreditFixture f = new CreditFixture();
    private final PayCreditUseCaseImpl useCase = f.payCredit();

    private PaymentCommand pay(Credit credit, String operationId, String amount, String payer) {
        return new PaymentCommand(credit.id().value(), new OperationId(operationId), Money.of(amount), payer);
    }

    private static boolean hasCode(Throwable e, String code) {
        return e instanceof BusinessRuleViolationException v && v.getErrorCode().equals(code);
    }

    @Test
    void aThirdPartyPaymentLowersTheBalanceRecordsItAndPublishesBothEvents() {
        Credit credit = f.givenCredit("cust-A", OwnerType.PERSONAL, "5000.00", TODAY.plusDays(90));

        PaymentResult result = useCase.execute(pay(credit, "op-00001", "300.00", "cust-B")).blockingGet();

        assertThat(result.resultingBalance()).isEqualTo(Money.of("4700.00"));
        assertThat(result.payerCustomerId()).isEqualTo("cust-B");
        assertThat(f.credits.get(credit.id()).outstandingBalance()).isEqualTo(Money.of("4700.00"));
        assertThat(f.operations.get("op-00001").recorded()).isTrue();
        assertThat(f.recorder.recorded()).hasSize(1);
        assertThat(f.events.ofType(CreditUpdated.class)).hasSize(1);
        assertThat(f.events.ofType(PaymentRegistered.class)).singleElement()
                .extracting(PaymentRegistered::payerCustomerId).isEqualTo("cust-B");
    }

    @Test
    void repeatingTheOperationIdGivesTheSameResultWithoutPayingTwice() {
        Credit credit = f.givenCredit("cust-A", OwnerType.PERSONAL, "5000.00", TODAY.plusDays(90));
        PaymentResult first = useCase.execute(pay(credit, "op-00001", "300.00", null)).blockingGet();

        PaymentResult second = useCase.execute(pay(credit, "op-00001", "300.00", null)).blockingGet();

        assertThat(second).isEqualTo(first);
        assertThat(f.credits.get(credit.id()).outstandingBalance()).isEqualTo(Money.of("4700.00"));
        assertThat(f.events.ofType(PaymentRegistered.class)).hasSize(1);
        assertThat(f.recorder.recorded()).hasSize(1);
    }

    @Test
    void reusingTheOperationIdWithAnotherAmountIsAConflict() {
        Credit credit = f.givenCredit("cust-A", OwnerType.PERSONAL, "5000.00", TODAY.plusDays(90));
        useCase.execute(pay(credit, "op-00001", "300.00", null)).test().assertComplete();

        useCase.execute(pay(credit, "op-00001", "301.00", null)).test()
                .assertError(e -> hasCode(e, "OPERATION_ID_REUSED"));
    }

    @Test
    void ifTheHistoryIsDownThePaymentStillSucceedsAndARepeatRetriesTheRecord() {
        Credit credit = f.givenCredit("cust-A", OwnerType.PERSONAL, "5000.00", TODAY.plusDays(90));
        f.recorder.down(true);

        useCase.execute(pay(credit, "op-00001", "300.00", null)).test().assertComplete();
        assertThat(f.operations.get("op-00001").recorded()).isFalse();

        f.recorder.down(false);
        useCase.execute(pay(credit, "op-00001", "300.00", null)).test().assertComplete();
        assertThat(f.operations.get("op-00001").recorded()).isTrue();
        assertThat(f.credits.get(credit.id()).outstandingBalance()).isEqualTo(Money.of("4700.00"));
    }

    @Test
    void aRejectedPaymentLeavesNoOperation() {
        Credit credit = f.givenCredit("cust-A", OwnerType.PERSONAL, "100.00", TODAY.plusDays(90));

        useCase.execute(pay(credit, "op-00001", "100.01", null)).test().assertError(e -> hasCode(e, "OVERPAYMENT"));

        assertThat(f.operations.size()).isZero();
        assertThat(f.events.published()).isEmpty();
    }

    @Test
    void anUnknownCreditIsNotFound() {
        useCase.execute(new PaymentCommand("missing", new OperationId("op-00001"), Money.of("1.00"), null)).test()
                .assertError(CreditNotFoundException.class);
    }

    @Test
    void payingTheLastOverdueProductInFullPublishesCleared() {
        Credit overdue = f.givenOverdueCredit("cust-A", "800.00");

        PaymentResult result = useCase.execute(pay(overdue, "op-00001", "800.00", null)).blockingGet();

        assertThat(result.status()).isEqualTo(CreditStatus.PAID.name());
        assertThat(f.events.ofType(CustomerOverdueCleared.class)).singleElement()
                .extracting(CustomerOverdueCleared::customerId).isEqualTo("cust-A");
    }

    @Test
    void doesNotPublishClearedWhileAnotherProductIsStillOverdue() {
        Credit overdue = f.givenOverdueCredit("cust-E", "800.00");
        f.givenOverdueCredit("cust-E", "50.00");

        useCase.execute(pay(overdue, "op-00001", "800.00", null)).test().assertComplete();

        assertThat(f.events.ofType(CustomerOverdueCleared.class)).isEmpty();
    }

    @Test
    void aPartialPaymentOfAnOverdueCreditDoesNotClearIt() {
        Credit overdue = f.givenOverdueCredit("cust-A", "800.00");

        useCase.execute(pay(overdue, "op-00001", "100.00", null)).test().assertComplete();

        assertThat(f.credits.get(overdue.id()).status()).isEqualTo(CreditStatus.OVERDUE);
        assertThat(f.events.ofType(CustomerOverdueCleared.class)).isEmpty();
    }
}

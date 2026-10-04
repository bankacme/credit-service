package com.bank.credit.application.usecase;

import static com.bank.credit.application.usecase.CreditFixture.TODAY;
import static org.assertj.core.api.Assertions.assertThat;

import com.bank.credit.application.command.OpenCreditCommand;
import com.bank.credit.domain.event.CreditOpened;
import com.bank.credit.domain.exception.BusinessRuleViolationException;
import com.bank.credit.domain.exception.CustomerNotFoundException;
import com.bank.credit.domain.model.Credit;
import com.bank.credit.domain.model.CreditStatus;
import com.bank.credit.domain.model.Money;
import com.bank.credit.domain.model.OwnerType;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.jupiter.api.Test;

class OpenCreditUseCaseImplTest {

    private final CreditFixture f = new CreditFixture();
    private final OpenCreditUseCaseImpl useCase = f.openCredit(false);

    private OpenCreditCommand command(String customerId, String amount) {
        return new OpenCreditCommand(customerId, Money.of(amount), TODAY.plusDays(90));
    }

    private static boolean hasCode(Throwable e, String code) {
        return e instanceof BusinessRuleViolationException v && v.getErrorCode().equals(code);
    }

    @Test
    void opensAPersonalCreditSavesItAndPublishesCreditOpened() {
        TestObserver<Credit> observer = useCase.execute(command("cust-A", "5000.00")).test();

        observer.assertComplete();
        Credit credit = observer.values().get(0);
        assertThat(credit.ownerType()).isEqualTo(OwnerType.PERSONAL);
        assertThat(credit.status()).isEqualTo(CreditStatus.ACTIVE);
        assertThat(f.credits.get(credit.id())).isNotNull();
        assertThat(f.events.ofType(CreditOpened.class)).hasSize(1);
    }

    @Test
    void aPersonalCustomerCannotHaveTwoUnpaidCredits() {
        useCase.execute(command("cust-A", "5000.00")).test().assertComplete();

        useCase.execute(command("cust-A", "100.00")).test()
                .assertError(e -> hasCode(e, "PERSONAL_CREDIT_LIMIT_REACHED"));
        assertThat(f.events.ofType(CreditOpened.class)).hasSize(1);
    }

    @Test
    void aBusinessCustomerCanHaveSeveralCredits() {
        useCase.execute(command("cust-E", "20000.00")).test().assertComplete();
        TestObserver<Credit> second = useCase.execute(command("cust-E", "8000.00")).test();

        second.assertComplete();
        assertThat(second.values().get(0).ownerType()).isEqualTo(OwnerType.BUSINESS);
    }

    @Test
    void anUnknownCustomerIsNotFoundAndAnInactiveOneIsRejected() {
        useCase.execute(command("nobody", "100.00")).test().assertError(CustomerNotFoundException.class);
        useCase.execute(command("cust-X", "100.00")).test().assertError(e -> hasCode(e, "CUSTOMER_INACTIVE"));
    }

    @Test
    void overdueDebtBlocksANewCredit() {
        f.givenOverdueCredit("cust-E", "100.00");

        useCase.execute(command("cust-E", "100.00")).test().assertError(e -> hasCode(e, "OVERDUE_DEBT"));
    }

    @Test
    void aPastDueDateIsOnlyAcceptedInDemoMode() {
        OpenCreditCommand pastDue = new OpenCreditCommand("cust-B", Money.of("800.00"), TODAY.minusDays(10));

        useCase.execute(pastDue).test().assertError(e -> hasCode(e, "INVALID_DUE_DATE"));
        f.openCredit(true).execute(pastDue).test().assertComplete();
    }
}

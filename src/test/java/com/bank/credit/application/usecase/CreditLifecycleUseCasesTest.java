package com.bank.credit.application.usecase;

import static com.bank.credit.application.usecase.CreditFixture.TODAY;
import static org.assertj.core.api.Assertions.assertThat;

import com.bank.credit.application.command.ChangeLimitCommand;
import com.bank.credit.application.command.ChargeCommand;
import com.bank.credit.application.command.PaymentCommand;
import com.bank.credit.application.command.RescheduleCreditCommand;
import com.bank.credit.application.port.in.CreditCardFilter;
import com.bank.credit.application.port.in.CreditFilter;
import com.bank.credit.application.view.CardBalanceView;
import com.bank.credit.application.view.PaymentInfoView;
import com.bank.credit.domain.event.CreditCardClosed;
import com.bank.credit.domain.event.CreditClosed;
import com.bank.credit.domain.event.CreditUpdated;
import com.bank.credit.domain.event.CustomerOverdueCleared;
import com.bank.credit.domain.exception.BusinessRuleViolationException;
import com.bank.credit.domain.exception.CreditCardNotFoundException;
import com.bank.credit.domain.exception.CreditNotFoundException;
import com.bank.credit.domain.model.CardStatus;
import com.bank.credit.domain.model.Credit;
import com.bank.credit.domain.model.CreditCard;
import com.bank.credit.domain.model.CreditCardId;
import com.bank.credit.domain.model.CreditId;
import com.bank.credit.domain.model.CreditStatus;
import com.bank.credit.domain.model.Money;
import com.bank.credit.domain.model.OperationId;
import com.bank.credit.domain.model.OwnerType;
import com.bank.credit.domain.model.ProductType;
import org.junit.jupiter.api.Test;

/** Consultas, reprogramación, cambio de línea, cierre, saldo y payment-info. */
class CreditLifecycleUseCasesTest {

    private final CreditFixture f = new CreditFixture();

    private static boolean hasCode(Throwable e, String code) {
        return e instanceof BusinessRuleViolationException v && v.getErrorCode().equals(code);
    }

    @Test
    void findsByIdAndFilters() {
        Credit a = f.givenCredit("cust-A", OwnerType.PERSONAL, "100.00", TODAY.plusDays(10));
        f.givenCredit("cust-E", OwnerType.BUSINESS, "200.00", TODAY.plusDays(10));

        new FindCreditUseCaseImpl(f.credits).execute(a.id()).test().assertValue(a);
        new FindCreditUseCaseImpl(f.credits).execute(new CreditId("missing")).test()
                .assertError(CreditNotFoundException.class);
        new FindCreditsUseCaseImpl(f.credits).execute(new CreditFilter("cust-A", null, null)).test()
                .assertValueCount(1);
        new FindCreditsUseCaseImpl(f.credits).execute(new CreditFilter(null, OwnerType.BUSINESS, CreditStatus.ACTIVE))
                .test().assertValueCount(1);
        new FindCreditsUseCaseImpl(f.credits).execute(null).test().assertValueCount(2);

        CreditCard card = f.givenCard("cust-A", "100.00");
        new FindCreditCardUseCaseImpl(f.cards).execute(card.id()).test().assertValue(card);
        new FindCreditCardUseCaseImpl(f.cards).execute(new CreditCardId("missing")).test()
                .assertError(CreditCardNotFoundException.class);
        new FindCreditCardsUseCaseImpl(f.cards).execute(new CreditCardFilter("cust-A", CardStatus.ACTIVE)).test()
                .assertValueCount(1);
    }

    @Test
    void reschedulingAnOverdueCreditReactivatesItAndClearsTheDebt() {
        Credit overdue = f.givenOverdueCredit("cust-A", "800.00");
        RescheduleCreditUseCaseImpl useCase = new RescheduleCreditUseCaseImpl(f.credits, f.events, f.clearance,
                false, f.clock);

        Credit rescheduled = useCase.execute(new RescheduleCreditCommand(overdue.id(), TODAY.plusDays(30)))
                .blockingGet();

        assertThat(rescheduled.status()).isEqualTo(CreditStatus.ACTIVE);
        assertThat(f.events.ofType(CreditUpdated.class)).hasSize(1);
        assertThat(f.events.ofType(CustomerOverdueCleared.class)).hasSize(1);
    }

    @Test
    void reschedulingAnActiveCreditDoesNotPublishCleared() {
        Credit credit = f.givenCredit("cust-A", OwnerType.PERSONAL, "800.00", TODAY.plusDays(5));

        new RescheduleCreditUseCaseImpl(f.credits, f.events, f.clearance, false, f.clock)
                .execute(new RescheduleCreditCommand(credit.id(), TODAY.plusDays(30))).test().assertComplete();

        assertThat(f.events.ofType(CustomerOverdueCleared.class)).isEmpty();
    }

    @Test
    void closesAPaidCreditOnceAndRejectsOneWithBalance() {
        Credit credit = f.givenCredit("cust-A", OwnerType.PERSONAL, "10.00", TODAY.plusDays(5));
        CloseCreditUseCaseImpl close = new CloseCreditUseCaseImpl(f.credits, f.events, f.clock);

        close.execute(credit.id()).test().assertError(e -> hasCode(e, "NOT_CLOSABLE"));

        f.payCredit().execute(new PaymentCommand(credit.id().value(), new OperationId("op-00001"),
                Money.of("10.00"), null)).blockingGet();
        close.execute(credit.id()).test().assertComplete();
        close.execute(credit.id()).test().assertComplete();

        assertThat(f.credits.get(credit.id()).status()).isEqualTo(CreditStatus.CLOSED);
        assertThat(f.events.ofType(CreditClosed.class)).hasSize(1);
    }

    @Test
    void changesTheLimitAndClosesACardWithNothingUsed() {
        CreditCard card = f.givenCard("cust-A", "2000.00");

        CreditCard changed = new ChangeCreditLimitUseCaseImpl(f.cards, f.events, f.clock)
                .execute(new ChangeLimitCommand(card.id(), Money.of("3000.00"))).blockingGet();
        new CloseCreditCardUseCaseImpl(f.cards, f.events, f.clock).execute(card.id()).test().assertComplete();

        assertThat(changed.creditLimit()).isEqualTo(Money.of("3000.00"));
        assertThat(f.cards.get(card.id()).status()).isEqualTo(CardStatus.CLOSED);
        assertThat(f.events.ofType(CreditCardClosed.class)).hasSize(1);
    }

    @Test
    void balanceAndPaymentInfoShowWhatIsOwed() {
        CreditCard card = f.givenCard("cust-A", "2000.00");
        f.chargeCard().execute(new ChargeCommand(card.id(), new OperationId("op-00001"), Money.of("500.00"), null))
                .blockingGet();
        Credit credit = f.givenCredit("cust-A", OwnerType.PERSONAL, "5000.00", TODAY.plusDays(90));

        CardBalanceView balance = new GetCreditCardBalanceUseCaseImpl(f.cards, f.clock).execute(card.id())
                .blockingGet();
        GetPaymentInfoUseCaseImpl info = new GetPaymentInfoUseCaseImpl(f.credits, f.cards);
        PaymentInfoView cardInfo = info.execute(ProductType.CREDIT_CARD, card.id().value()).blockingGet();
        PaymentInfoView creditInfo = info.execute(ProductType.CREDIT, credit.id().value()).blockingGet();

        assertThat(balance.availableCredit()).isEqualTo(Money.of("1500.00"));
        assertThat(balance.asOf()).isEqualTo(f.clock.instant());
        assertThat(cardInfo.amountDue()).isEqualTo(Money.of("500.00"));
        assertThat(cardInfo.maskedNumber()).startsWith("**** ");
        assertThat(creditInfo.amountDue()).isEqualTo(Money.of("5000.00"));
        assertThat(creditInfo.maskedNumber()).isNull();
        info.execute(ProductType.CREDIT, "missing").test().assertError(CreditNotFoundException.class);
    }
}

package com.bank.credit.application.usecase;

import static com.bank.credit.application.usecase.CreditFixture.TERM_DAYS;
import static com.bank.credit.application.usecase.CreditFixture.TODAY;
import static org.assertj.core.api.Assertions.assertThat;

import com.bank.credit.application.command.ChargeCommand;
import com.bank.credit.application.view.OverdueCheckResult;
import com.bank.credit.domain.event.CreditCardUpdated;
import com.bank.credit.domain.event.CreditUpdated;
import com.bank.credit.domain.event.ProductBecameOverdue;
import com.bank.credit.domain.model.CardStatus;
import com.bank.credit.domain.model.Credit;
import com.bank.credit.domain.model.CreditCard;
import com.bank.credit.domain.model.CreditStatus;
import com.bank.credit.domain.model.Money;
import com.bank.credit.domain.model.OperationId;
import com.bank.credit.domain.model.OwnerType;
import org.junit.jupiter.api.Test;

class CheckOverdueUseCaseImplTest {

    private final CreditFixture f = new CreditFixture();

    private CreditCard chargedCard(String customerId) {
        CreditCard card = f.givenCard(customerId, "2000.00");
        f.chargeCard().execute(new ChargeCommand(card.id(), new OperationId("op-" + customerId + "-1"),
                Money.of("500.00"), null)).blockingGet();
        return f.cards.get(card.id());
    }

    @Test
    void marksPastDueCreditsWithBalanceAndPublishesOneNoticePerProduct() {
        Credit dueYesterday = f.givenCredit("cust-C", OwnerType.PERSONAL, "800.00", TODAY.minusDays(10));
        f.givenCredit("cust-A", OwnerType.PERSONAL, "5000.00", TODAY.plusDays(90));

        OverdueCheckResult result = f.checkOverdue().execute(null).blockingGet();

        assertThat(result.asOf()).isEqualTo(TODAY);
        assertThat(result.creditsMarked()).isEqualTo(1);
        assertThat(result.cardsMarked()).isZero();
        assertThat(result.customersAffected()).isEqualTo(1);
        assertThat(f.credits.get(dueYesterday.id()).status()).isEqualTo(CreditStatus.OVERDUE);
        assertThat(f.events.ofType(CreditUpdated.class)).hasSize(1);
        assertThat(f.events.ofType(ProductBecameOverdue.class)).singleElement()
                .extracting(ProductBecameOverdue::productType).isEqualTo("CREDIT");
    }

    @Test
    void runningItAgainChangesAndPublishesNothing() {
        f.givenCredit("cust-C", OwnerType.PERSONAL, "800.00", TODAY.minusDays(10));
        f.checkOverdue().execute(null).test().assertComplete();
        f.events.clear();

        OverdueCheckResult second = f.checkOverdue().execute(null).blockingGet();

        assertThat(second.creditsMarked()).isZero();
        assertThat(f.events.published()).isEmpty();
    }

    @Test
    void asOfSimulatesAnotherDateForCards() {
        CreditCard card = chargedCard("cust-A");

        assertThat(f.checkOverdue().execute(TODAY.plusDays(TERM_DAYS)).blockingGet().cardsMarked()).isZero();
        OverdueCheckResult result = f.checkOverdue().execute(TODAY.plusDays(TERM_DAYS + 10)).blockingGet();

        assertThat(result.cardsMarked()).isEqualTo(1);
        assertThat(f.cards.get(card.id()).status()).isEqualTo(CardStatus.OVERDUE);
        assertThat(f.events.ofType(CreditCardUpdated.class)).isNotEmpty();
    }

    @Test
    void countsEachCustomerOnceEvenWithSeveralProducts() {
        f.givenCredit("cust-E", OwnerType.BUSINESS, "100.00", TODAY.minusDays(1));
        f.givenCredit("cust-E", OwnerType.BUSINESS, "200.00", TODAY.minusDays(1));
        chargedCard("cust-E");
        f.givenCredit("cust-C", OwnerType.PERSONAL, "800.00", TODAY.minusDays(1));

        OverdueCheckResult result = f.checkOverdue().execute(TODAY.plusDays(TERM_DAYS + 1)).blockingGet();

        assertThat(result.creditsMarked()).isEqualTo(3);
        assertThat(result.cardsMarked()).isEqualTo(1);
        assertThat(result.customersAffected()).isEqualTo(2);
    }

    @Test
    void aVersionConflictIsRetriedAfterReloading() {
        Credit due = f.givenCredit("cust-C", OwnerType.PERSONAL, "800.00", TODAY.minusDays(1));
        f.credits.failNextSavesWithConflict(2);

        OverdueCheckResult result = f.checkOverdue().execute(null).blockingGet();

        assertThat(result.creditsMarked()).isEqualTo(1);
        assertThat(f.credits.get(due.id()).status()).isEqualTo(CreditStatus.OVERDUE);
    }

    @Test
    void aProductThatKeepsFailingIsSkippedWithoutStoppingTheOthers() {
        f.givenCredit("cust-C", OwnerType.PERSONAL, "800.00", TODAY.minusDays(1));
        f.credits.failNextSavesWithConflict(CheckOverdueUseCaseImpl.MAX_ATTEMPTS);
        CreditCard card = chargedCard("cust-A");

        OverdueCheckResult result = f.checkOverdue().execute(TODAY.plusDays(TERM_DAYS + 1)).blockingGet();

        assertThat(result.creditsMarked()).isZero();
        assertThat(result.cardsMarked()).isEqualTo(1);
        assertThat(f.cards.get(card.id()).status()).isEqualTo(CardStatus.OVERDUE);
    }
}

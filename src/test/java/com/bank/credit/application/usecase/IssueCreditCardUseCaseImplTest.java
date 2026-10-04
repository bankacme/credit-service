package com.bank.credit.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.credit.application.command.IssueCreditCardCommand;
import com.bank.credit.domain.event.CreditCardIssued;
import com.bank.credit.domain.exception.BusinessRuleViolationException;
import com.bank.credit.domain.exception.CardNumberCollisionException;
import com.bank.credit.domain.model.CardNumber;
import com.bank.credit.domain.model.CreditCard;
import com.bank.credit.domain.model.Money;
import com.bank.credit.domain.service.CardNumberGenerator;
import io.reactivex.rxjava3.observers.TestObserver;
import java.util.Random;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

class IssueCreditCardUseCaseImplTest {

    private final CreditFixture f = new CreditFixture();

    private IssueCreditCardCommand command(String customerId) {
        return new IssueCreditCardCommand(customerId, Money.of("2000.00"));
    }

    @Test
    void issuesACardAndPublishesCreditCardIssued() {
        TestObserver<CreditCard> observer = f.issueCard(new Random()).execute(command("cust-A")).test();

        observer.assertComplete();
        CreditCard card = observer.values().get(0);
        assertThat(card.cardNumber().value()).startsWith("400000");
        assertThat(card.availableCredit()).isEqualTo(Money.of("2000.00"));
        assertThat(f.events.ofType(CreditCardIssued.class)).hasSize(1);
    }

    @Test
    void anUnpaidPersonalCreditDoesNotBlockACard() {
        f.givenCredit("cust-A", com.bank.credit.domain.model.OwnerType.PERSONAL, "5000.00",
                CreditFixture.TODAY.plusDays(90));

        f.issueCard(new Random()).execute(command("cust-A")).test().assertComplete();
    }

    @Test
    void generatesAnotherNumberWhenTheFirstOneIsTaken() {
        CardNumber taken = new CardNumberGenerator().generate(new Random(7));
        f.givenCard("cust-B", "100.00", taken);

        // El mismo seed genera primero el número ya usado; el segundo intento sale distinto.
        CreditCard issued = f.issueCard(new Random(7)).execute(command("cust-A")).blockingGet();

        assertThat(issued.cardNumber()).isNotEqualTo(taken);
        assertThat(f.cards.size()).isEqualTo(2);
    }

    @Test
    void givesUpAfterThreeCollisions() {
        CardNumber taken = new CardNumberGenerator().generate(new Random(7));
        f.givenCard("cust-B", "100.00", taken);
        RandomGenerator alwaysSame = new RandomGenerator() {
            private Random seeded = new Random(7);
            private int calls;

            @Override
            public long nextLong() {
                return seeded.nextLong();
            }

            @Override
            public int nextInt(int bound) {
                if (calls++ % 9 == 0) {
                    seeded = new Random(7);
                }
                return seeded.nextInt(bound);
            }
        };

        // Un generador que siempre produce el número ya usado: 3 intentos y se rinde.
        new IssueCreditCardUseCaseImpl(f.customers, f.cards, f.overdue, f.events, f.policy,
                new CardNumberGenerator(), alwaysSame, f.clock)
                .execute(command("cust-A")).test().assertError(CardNumberCollisionException.class);
    }

    @Test
    void overdueDebtBlocksANewCard() {
        f.givenOverdueCredit("cust-B", "50.00");

        f.issueCard(new Random()).execute(command("cust-B")).test()
                .assertError(e -> e instanceof BusinessRuleViolationException v
                        && v.getErrorCode().equals("OVERDUE_DEBT"));
    }
}

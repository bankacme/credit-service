package com.bank.credit.infrastructure.adapter.out.persistence;

import static com.bank.credit.infrastructure.fixture.CreditFixtures.CLOCK;
import static com.bank.credit.infrastructure.fixture.CreditFixtures.TODAY;
import static org.assertj.core.api.Assertions.assertThat;

import com.bank.credit.domain.exception.CardNumberCollisionException;
import com.bank.credit.domain.model.CardNumber;
import com.bank.credit.domain.model.CreditCard;
import com.bank.credit.domain.model.Money;
import com.bank.credit.domain.model.OperationId;
import com.bank.credit.domain.model.OwnerType;
import com.bank.credit.infrastructure.fixture.CreditFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CreditCardPersistenceAdapterTest extends MongoTestSupport {

    @Autowired
    private CreditCardPersistenceAdapter adapter;

    @Autowired
    private CreditPersistenceAdapter creditAdapter;

    @Autowired
    private OverdueQueryAdapter overdueQuery;

    @Test
    void savesAndReadsBackACardWithItsPaymentDueDate() {
        CreditCard charged = CreditFixtures.card("cust-A", "2000.00")
                .charge(new OperationId("op-00001"), Money.of("500.00"), 30, CLOCK).card();

        CreditCard saved = adapter.save(charged).blockingGet();

        assertThat(adapter.findById(saved.id()).blockingGet()).isEqualTo(saved);
        assertThat(saved.paymentDueDate()).isEqualTo(TODAY.plusDays(30));
    }

    @Test
    void aRepeatedCardNumberIsACollision() {
        CardNumber number = new CardNumber("4000001234564821");
        adapter.save(CreditFixtures.card("cust-A", "100.00", number)).blockingGet();

        observe(adapter.save(CreditFixtures.card("cust-B", "100.00", number)))
                .assertError(CardNumberCollisionException.class);
    }

    @Test
    void findsActiveCardsWhosePaymentDateHasPassed() {
        CreditCard charged = adapter.save(CreditFixtures.card("cust-A", "2000.00")
                .charge(new OperationId("op-00001"), Money.of("1.00"), 30, CLOCK).card()).blockingGet();
        adapter.save(CreditFixtures.card("cust-B", "2000.00")).blockingGet();

        assertThat(adapter.findActiveDueBefore(TODAY.plusDays(30)).toList().blockingGet()).isEmpty();
        assertThat(adapter.findActiveDueBefore(TODAY.plusDays(31)).toList().blockingGet())
                .extracting(CreditCard::id).containsExactly(charged.id());
    }

    @Test
    void overdueDebtLooksAtCreditsAndCards() {
        CreditCard card = adapter.save(CreditFixtures.card("cust-A", "2000.00")
                .charge(new OperationId("op-00001"), Money.of("1.00"), 30, CLOCK).card()).blockingGet();
        creditAdapter.save(CreditFixtures.credit("cust-C", OwnerType.PERSONAL, "800.00", TODAY.minusDays(1))
                .markOverdueIfDue(TODAY, CLOCK).orElseThrow()).blockingGet();

        assertThat(overdueQuery.existsOverdueByCustomer("cust-A").blockingGet()).isFalse();
        assertThat(overdueQuery.existsOverdueByCustomer("cust-C").blockingGet()).isTrue();

        adapter.save(card.markOverdueIfDue(TODAY.plusDays(31), CLOCK).orElseThrow()).blockingGet();
        assertThat(overdueQuery.existsOverdueByCustomer("cust-A").blockingGet()).isTrue();
    }
}

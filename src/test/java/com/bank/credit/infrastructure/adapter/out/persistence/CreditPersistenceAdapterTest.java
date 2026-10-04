package com.bank.credit.infrastructure.adapter.out.persistence;

import static com.bank.credit.infrastructure.fixture.CreditFixtures.CLOCK;
import static com.bank.credit.infrastructure.fixture.CreditFixtures.TODAY;
import static org.assertj.core.api.Assertions.assertThat;

import com.bank.credit.application.port.in.CreditFilter;
import com.bank.credit.domain.model.Credit;
import com.bank.credit.domain.model.CreditStatus;
import com.bank.credit.domain.model.Money;
import com.bank.credit.domain.model.OperationId;
import com.bank.credit.domain.model.OwnerType;
import com.bank.credit.infrastructure.fixture.CreditFixtures;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CreditPersistenceAdapterTest extends MongoTestSupport {

    @Autowired
    private CreditPersistenceAdapter adapter;

    private Credit saved(String customerId, OwnerType ownerType, String amount, LocalDate dueDate) {
        return adapter.save(CreditFixtures.credit(customerId, ownerType, amount, dueDate)).blockingGet();
    }

    @Test
    void savesAndReadsBackTheSameCredit() {
        Credit credit = saved("cust-A", OwnerType.PERSONAL, "5000.00", TODAY.plusDays(90));

        assertThat(adapter.findById(credit.id()).blockingGet()).isEqualTo(credit);
        assertThat(creditRepository.findById(credit.id().value()).blockingGet().getUnpaidPersonal()).isTrue();
    }

    @Test
    void theUniquePartialIndexStopsASecondUnpaidPersonalCredit() {
        saved("cust-A", OwnerType.PERSONAL, "5000.00", TODAY.plusDays(90));

        observe(adapter.save(CreditFixtures.credit("cust-A", OwnerType.PERSONAL, "100.00", TODAY.plusDays(90))))
                .assertError(e -> hasCode(e, "PERSONAL_CREDIT_LIMIT_REACHED"));
    }

    @Test
    void aBusinessCustomerAndAPaidPersonalCreditDoNotCountForTheIndex() {
        saved("cust-E", OwnerType.BUSINESS, "20000.00", TODAY.plusDays(90));
        saved("cust-E", OwnerType.BUSINESS, "8000.00", TODAY.plusDays(90));

        Credit personal = saved("cust-A", OwnerType.PERSONAL, "100.00", TODAY.plusDays(90));
        adapter.save(personal.registerPayment(new OperationId("op-00001"), Money.of("100.00"), null, CLOCK)
                .credit()).blockingGet();

        observe(adapter.save(CreditFixtures.credit("cust-A", OwnerType.PERSONAL, "200.00", TODAY.plusDays(90))))
                .assertComplete();
        assertThat(adapter.existsUnpaidByCustomer("cust-A").blockingGet()).isTrue();
    }

    @Test
    void savingAStaleVersionIsAConcurrentModification() {
        Credit credit = saved("cust-A", OwnerType.PERSONAL, "5000.00", TODAY.plusDays(90));
        adapter.save(credit.reschedule(TODAY.plusDays(100), false, CLOCK)).blockingGet();

        observe(adapter.save(credit.reschedule(TODAY.plusDays(120), false, CLOCK)))
                .assertError(e -> hasCode(e, "CONCURRENT_MODIFICATION"));
    }

    @Test
    void findsActiveCreditsDueBeforeADateComparingTheDatesAsText() {
        Credit dueEarlier = saved("cust-1", OwnerType.BUSINESS, "1.00", LocalDate.of(2026, 9, 30));
        saved("cust-2", OwnerType.BUSINESS, "1.00", LocalDate.of(2026, 10, 2));
        saved("cust-3", OwnerType.BUSINESS, "1.00", LocalDate.of(2026, 12, 1));

        assertThat(adapter.findActiveDueBefore(LocalDate.of(2026, 10, 2)).toList().blockingGet())
                .extracting(Credit::id).containsExactly(dueEarlier.id());
    }

    @Test
    void filtersByCustomerOwnerTypeAndStatus() {
        saved("cust-A", OwnerType.PERSONAL, "100.00", TODAY.plusDays(10));
        saved("cust-E", OwnerType.BUSINESS, "100.00", TODAY.plusDays(10));

        assertThat(adapter.findAll(new CreditFilter("cust-A", null, null)).count().blockingGet()).isEqualTo(1);
        assertThat(adapter.findAll(new CreditFilter(null, OwnerType.BUSINESS, CreditStatus.ACTIVE)).count()
                .blockingGet()).isEqualTo(1);
        assertThat(adapter.findAll(CreditFilter.all()).count().blockingGet()).isEqualTo(2);
    }
}

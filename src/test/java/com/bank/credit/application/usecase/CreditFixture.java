package com.bank.credit.application.usecase;

import com.bank.credit.application.usecase.TestAdapters.FakeMovementRecorderPort;
import com.bank.credit.application.usecase.TestAdapters.PassthroughUnitOfWorkPort;
import com.bank.credit.application.usecase.TestAdapters.RecordingEventPublisherPort;
import com.bank.credit.application.usecase.TestAdapters.RepositoryOverdueQueryPort;
import com.bank.credit.application.usecase.TestAdapters.StubCustomerLookupPort;
import com.bank.credit.domain.model.CardNumber;
import com.bank.credit.domain.model.Credit;
import com.bank.credit.domain.model.CreditCard;
import com.bank.credit.domain.model.CustomerSnapshot;
import com.bank.credit.domain.model.CustomerType;
import com.bank.credit.domain.model.Money;
import com.bank.credit.domain.model.OwnerType;
import com.bank.credit.domain.service.AcquisitionPolicy;
import com.bank.credit.domain.service.CardNumberGenerator;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Random;

/** Todos los casos de uso cableados con adaptadores en memoria y un reloj fijo (2026-10-01). */
class CreditFixture {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 1);
    static final int TERM_DAYS = 30;

    final Clock clock = Clock.fixed(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
    final InMemoryCreditRepository credits = new InMemoryCreditRepository();
    final InMemoryCreditCardRepository cards = new InMemoryCreditCardRepository();
    final InMemoryOperationLog operations = new InMemoryOperationLog();
    final StubCustomerLookupPort customers = new StubCustomerLookupPort()
            .with(new CustomerSnapshot("cust-A", CustomerType.PERSONAL, "ACTIVE"))
            .with(new CustomerSnapshot("cust-B", CustomerType.PERSONAL, "ACTIVE"))
            .with(new CustomerSnapshot("cust-E", CustomerType.BUSINESS, "ACTIVE"))
            .with(new CustomerSnapshot("cust-X", CustomerType.PERSONAL, "INACTIVE"));
    final RepositoryOverdueQueryPort overdue = new RepositoryOverdueQueryPort(credits, cards);
    final PassthroughUnitOfWorkPort unitOfWork = new PassthroughUnitOfWorkPort(credits, cards, operations);
    final RecordingEventPublisherPort events = new RecordingEventPublisherPort();
    final FakeMovementRecorderPort recorder = new FakeMovementRecorderPort();
    final HistoryRecorder historyRecorder = new HistoryRecorder(recorder, operations, clock);
    final OverdueClearance clearance = new OverdueClearance(overdue, events, clock);
    final AcquisitionPolicy policy = new AcquisitionPolicy();

    OpenCreditUseCaseImpl openCredit(boolean demoMode) {
        return new OpenCreditUseCaseImpl(customers, credits, overdue, events, policy, demoMode, clock);
    }

    IssueCreditCardUseCaseImpl issueCard(Random random) {
        return new IssueCreditCardUseCaseImpl(customers, cards, overdue, events, policy, new CardNumberGenerator(),
                random, clock);
    }

    PayCreditUseCaseImpl payCredit() {
        return new PayCreditUseCaseImpl(credits, operations, unitOfWork, events, clearance, historyRecorder, clock);
    }

    PayCreditCardUseCaseImpl payCard() {
        return new PayCreditCardUseCaseImpl(cards, operations, unitOfWork, events, clearance, historyRecorder,
                clock);
    }

    ChargeCreditCardUseCaseImpl chargeCard() {
        return new ChargeCreditCardUseCaseImpl(cards, operations, unitOfWork, events, historyRecorder, TERM_DAYS,
                clock);
    }

    CheckOverdueUseCaseImpl checkOverdue() {
        return new CheckOverdueUseCaseImpl(credits, cards, events, clock);
    }

    /** Un crédito guardado directamente (sin pasar por el caso de uso ni publicar). */
    Credit givenCredit(String customerId, OwnerType ownerType, String amount, LocalDate dueDate) {
        return credits.save(Credit.open(customerId, ownerType, Money.of(amount), dueDate, true, clock)).blockingGet();
    }

    Credit givenOverdueCredit(String customerId, String amount) {
        Credit credit = givenCredit(customerId, OwnerType.PERSONAL, amount, TODAY.minusDays(1));
        return credits.save(credit.markOverdueIfDue(TODAY, clock).orElseThrow()).blockingGet();
    }

    CreditCard givenCard(String customerId, String limit) {
        return cards.save(CreditCard.issue(customerId, OwnerType.PERSONAL,
                new CardNumberGenerator().generate(new Random()), Money.of(limit), clock)).blockingGet();
    }

    CreditCard givenCard(String customerId, String limit, CardNumber number) {
        return cards.save(CreditCard.issue(customerId, OwnerType.PERSONAL, number, Money.of(limit), clock))
                .blockingGet();
    }
}

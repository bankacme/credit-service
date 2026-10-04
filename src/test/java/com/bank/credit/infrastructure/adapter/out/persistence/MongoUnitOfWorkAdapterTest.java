package com.bank.credit.infrastructure.adapter.out.persistence;

import static com.bank.credit.infrastructure.fixture.CreditFixtures.CLOCK;
import static com.bank.credit.infrastructure.fixture.CreditFixtures.TODAY;
import static org.assertj.core.api.Assertions.assertThat;

import com.bank.credit.domain.model.Credit;
import com.bank.credit.domain.model.CreditCard;
import com.bank.credit.domain.model.CreditOperation;
import com.bank.credit.domain.model.Money;
import com.bank.credit.domain.model.OperationId;
import com.bank.credit.domain.model.OwnerType;
import com.bank.credit.infrastructure.fixture.CreditFixtures;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** El producto y su operación se guardan juntos o ninguno (transacción real de Mongo, replica set rs0). */
class MongoUnitOfWorkAdapterTest extends MongoTestSupport {

    @Autowired
    private MongoUnitOfWorkAdapter unitOfWork;

    @Autowired
    private CreditPersistenceAdapter creditAdapter;

    @Autowired
    private CreditCardPersistenceAdapter cardAdapter;

    @Autowired
    private OperationLogPersistenceAdapter operationLog;

    @Test
    void savesACreditPaymentAndItsOperationTogether() {
        Credit credit = creditAdapter.save(CreditFixtures.credit("cust-A", OwnerType.PERSONAL, "5000.00",
                TODAY.plusDays(90))).blockingGet();
        Credit.PaymentOutcome outcome = credit.registerPayment(new OperationId("op-00001"), Money.of("300.00"),
                "cust-B", CLOCK);
        CreditOperation operation = CreditOperation.ofPayment(outcome.result(), credit.customerId(),
                CLOCK.instant());

        Credit saved = unitOfWork.saveCreditAndOperation(outcome.credit(), operation).blockingGet();

        assertThat(creditAdapter.findById(credit.id()).blockingGet().outstandingBalance())
                .isEqualTo(Money.of("4700.00"));
        assertThat(saved.version()).isGreaterThan(credit.version());
        assertThat(operationLog.find(new OperationId("op-00001")).blockingGet()).isEqualTo(operation);
    }

    @Test
    void savesACardChargeAndItsOperationTogether() {
        CreditCard card = cardAdapter.save(CreditFixtures.card("cust-A", "2000.00")).blockingGet();
        CreditCard.ChargeOutcome outcome = card.charge(new OperationId("op-00002"), Money.of("500.00"), 30, CLOCK);
        CreditOperation operation = CreditOperation.ofCharge(outcome.result(), card.customerId(), "Compra",
                CLOCK.instant());

        unitOfWork.saveCardAndOperation(outcome.card(), operation).blockingGet();

        assertThat(cardAdapter.findById(card.id()).blockingGet().usedAmount()).isEqualTo(Money.of("500.00"));
        assertThat(operationRepository.existsById("op-00002").blockingGet()).isTrue();
    }

    @Test
    void aFailureInsideTheTransactionRollsBackTheProduct() {
        Credit credit = CreditFixtures.credit("cust-A", OwnerType.PERSONAL, "100.00", TODAY.plusDays(90));

        observe(unitOfWork.saveCreditForcingFailureAfterward(credit)).assertError(IllegalStateException.class);

        assertThat(creditRepository.existsById(credit.id().value()).blockingGet()).isFalse();
    }

    @Test
    void aRepeatedOperationIdRollsBackTheSecondPayment() {
        Credit credit = creditAdapter.save(CreditFixtures.credit("cust-E", OwnerType.BUSINESS, "5000.00",
                TODAY.plusDays(90))).blockingGet();
        Credit.PaymentOutcome first = credit.registerPayment(new OperationId("op-dup-01"), Money.of("100.00"), null,
                CLOCK);
        Credit afterFirst = unitOfWork.saveCreditAndOperation(first.credit(),
                CreditOperation.ofPayment(first.result(), credit.customerId(), CLOCK.instant())).blockingGet();

        Credit.PaymentOutcome second = afterFirst.registerPayment(new OperationId("op-dup-01"), Money.of("100.00"),
                null, CLOCK);
        observe(unitOfWork.saveCreditAndOperation(second.credit(),
                        CreditOperation.ofPayment(second.result(), credit.customerId(), CLOCK.instant())))
                .assertError(e -> hasCode(e, "CONCURRENT_MODIFICATION"));

        assertThat(creditAdapter.findById(credit.id()).blockingGet().outstandingBalance())
                .isEqualTo(Money.of("4900.00"));
    }

    @Test
    void findsOnlyUnrecordedOperationsOlderThanTheThreshold() {
        Credit credit = creditAdapter.save(CreditFixtures.credit("cust-E", OwnerType.BUSINESS, "5000.00",
                TODAY.plusDays(90))).blockingGet();
        CreditOperation pending = CreditFixtures.paymentOf(credit, "op-00010", "1.00", null);
        CreditOperation recorded = CreditFixtures.paymentOf(credit, "op-00011", "1.00", null)
                .markRecorded(CLOCK.instant());
        operationLog.save(pending).blockingGet();
        operationLog.save(recorded).blockingGet();
        Instant later = CLOCK.instant().plus(Duration.ofMinutes(5));

        assertThat(operationLog.findUnrecordedOlderThan(later).toList().blockingGet())
                .extracting(CreditOperation::operationId).containsExactly(new OperationId("op-00010"));
        assertThat(operationLog.findUnrecordedOlderThan(CLOCK.instant()).toList().blockingGet()).isEmpty();
    }
}

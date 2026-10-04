package com.bank.credit.application.usecase;

import static com.bank.credit.application.usecase.CreditFixture.TODAY;
import static org.assertj.core.api.Assertions.assertThat;

import com.bank.credit.application.command.PaymentCommand;
import com.bank.credit.application.view.RecoveryResult;
import com.bank.credit.domain.model.Credit;
import com.bank.credit.domain.model.Money;
import com.bank.credit.domain.model.OperationId;
import com.bank.credit.domain.model.OwnerType;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class RecoverUnrecordedOperationsUseCaseImplTest {

    private final CreditFixture f = new CreditFixture();

    private RecoverUnrecordedOperationsUseCaseImpl recoveryAt(Clock clock) {
        return new RecoverUnrecordedOperationsUseCaseImpl(f.operations,
                new HistoryRecorder(f.recorder, f.operations, clock), 2, clock);
    }

    private void payWhileHistoryIsDown(String operationId) {
        Credit credit = f.givenCredit("cust-E", OwnerType.BUSINESS, "1000.00", TODAY.plusDays(90));
        f.recorder.down(true);
        f.payCredit().execute(new PaymentCommand(credit.id().value(), new OperationId(operationId),
                Money.of("10.00"), null)).blockingGet();
        f.recorder.down(false);
    }

    @Test
    void recordsWhatWasPendingForLongerThanTheThreshold() {
        payWhileHistoryIsDown("op-00001");
        payWhileHistoryIsDown("op-00002");

        RecoveryResult result = recoveryAt(Clock.offset(f.clock, Duration.ofMinutes(5))).execute(null).blockingGet();

        assertThat(result.olderThanMinutes()).isEqualTo(2);
        assertThat(result.found()).isEqualTo(2);
        assertThat(result.recorded()).isEqualTo(2);
        assertThat(result.stillPending()).isZero();
        assertThat(f.operations.get("op-00001").recorded()).isTrue();
    }

    @Test
    void ignoresOperationsYoungerThanTheThreshold() {
        payWhileHistoryIsDown("op-00001");

        RecoveryResult result = recoveryAt(Clock.offset(f.clock, Duration.ofMinutes(1))).execute(null).blockingGet();

        assertThat(result.found()).isZero();
    }

    @Test
    void ifTheHistoryIsStillDownTheyStayPending() {
        payWhileHistoryIsDown("op-00001");
        f.recorder.down(true);

        RecoveryResult result = recoveryAt(Clock.offset(f.clock, Duration.ofMinutes(5))).execute(0).blockingGet();

        assertThat(result.found()).isEqualTo(1);
        assertThat(result.stillPending()).isEqualTo(1);
        assertThat(f.operations.get("op-00001").recorded()).isFalse();
    }
}

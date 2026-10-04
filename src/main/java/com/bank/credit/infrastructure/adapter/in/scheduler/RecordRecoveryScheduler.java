package com.bank.credit.infrastructure.adapter.in.scheduler;

import com.bank.credit.application.port.in.RecoverUnrecordedOperationsUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Reintenta registrar en el historial lo pendiente (credit.recorder.cron, cada minuto). P1/P2. */
@Component
@Slf4j
public class RecordRecoveryScheduler {

    private final RecoverUnrecordedOperationsUseCase recoverUseCase;

    public RecordRecoveryScheduler(RecoverUnrecordedOperationsUseCase recoverUseCase) {
        this.recoverUseCase = recoverUseCase;
    }

    @Scheduled(cron = "${credit.recorder.cron:0 * * * * *}", zone = "${bank.zone}")
    public void retryPendingRecords() {
        recoverUseCase.execute(null).subscribe(
                result -> {
                    if (result.found() > 0) {
                        log.info("Record recovery: found={} recorded={} stillPending={}", result.found(),
                                result.recorded(), result.stillPending());
                    }
                },
                error -> log.error("Record recovery failed", error));
    }
}

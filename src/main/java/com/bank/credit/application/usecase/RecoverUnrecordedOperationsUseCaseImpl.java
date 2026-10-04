package com.bank.credit.application.usecase;

import com.bank.credit.application.port.in.RecoverUnrecordedOperationsUseCase;
import com.bank.credit.application.port.out.OperationLogPort;
import com.bank.credit.application.view.RecoveryResult;
import io.reactivex.rxjava3.core.Single;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Reintenta registrar en el historial las operaciones con recorded = false de más de N minutos
 * (data-model 2.6). transaction-service es idempotente por operationId, así que reintentar es seguro.
 */
public class RecoverUnrecordedOperationsUseCaseImpl implements RecoverUnrecordedOperationsUseCase {

    private final OperationLogPort operationLogPort;
    private final HistoryRecorder historyRecorder;
    private final int defaultOlderThanMinutes;
    private final Clock clock;

    public RecoverUnrecordedOperationsUseCaseImpl(OperationLogPort operationLogPort, HistoryRecorder historyRecorder,
                                                  int defaultOlderThanMinutes, Clock clock) {
        this.operationLogPort = operationLogPort;
        this.historyRecorder = historyRecorder;
        this.defaultOlderThanMinutes = defaultOlderThanMinutes;
        this.clock = clock;
    }

    @Override
    public Single<RecoveryResult> execute(Integer olderThanMinutesOverride) {
        int olderThanMinutes = olderThanMinutesOverride != null ? olderThanMinutesOverride : defaultOlderThanMinutes;
        Instant threshold = clock.instant().minus(Duration.ofMinutes(olderThanMinutes));
        return operationLogPort.findUnrecordedOlderThan(threshold)
                .flatMapSingle(historyRecorder::tryRecord)
                .toList()
                .map(results -> {
                    int recorded = (int) results.stream().filter(Boolean::booleanValue).count();
                    return new RecoveryResult(clock.instant(), olderThanMinutes, results.size(), recorded,
                            results.size() - recorded);
                });
    }
}

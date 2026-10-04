package com.bank.credit.application.usecase;

import com.bank.credit.application.port.out.MovementRecorderPort;
import com.bank.credit.application.port.out.OperationLogPort;
import com.bank.credit.domain.model.CreditOperation;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import java.time.Clock;

/**
 * Registra un pago o consumo ya aplicado en el historial (data-model 2.6). Si el registro falla, el
 * error se traga: la operación queda {@code recorded = false} y la retoman el reintento programado,
 * {@code POST /credit-recovery-runs} o una repetición del mismo operationId. El pago o consumo ya
 * ocurrió, así que la respuesta al cliente nunca depende de esto.
 */
public class HistoryRecorder {

    private final MovementRecorderPort movementRecorderPort;
    private final OperationLogPort operationLogPort;
    private final Clock clock;

    public HistoryRecorder(MovementRecorderPort movementRecorderPort, OperationLogPort operationLogPort,
                           Clock clock) {
        this.movementRecorderPort = movementRecorderPort;
        this.operationLogPort = operationLogPort;
        this.clock = clock;
    }

    /** true si quedó registrado; false si falló (sin propagar el error). */
    public Single<Boolean> tryRecord(CreditOperation operation) {
        if (operation.recorded()) {
            return Single.just(true);
        }
        return movementRecorderPort.record(operation)
                .andThen(Single.defer(() -> operationLogPort.save(operation.markRecorded(clock.instant()))))
                .map(saved -> true)
                .onErrorReturnItem(false);
    }

    /** Lo mismo, como Completable que nunca falla. */
    public Completable recordQuietly(CreditOperation operation) {
        return tryRecord(operation).ignoreElement();
    }
}

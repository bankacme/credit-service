package com.bank.credit.application.port.out;

import com.bank.credit.domain.model.CreditOperation;
import io.reactivex.rxjava3.core.Completable;

/**
 * Registra un pago o consumo en el historial: POST /transactions/records de transaction-service en
 * P1/P2 (idempotente por operationId), no-op en P3 (lo cubren los eventos).
 */
public interface MovementRecorderPort {

    Completable record(CreditOperation operation);
}

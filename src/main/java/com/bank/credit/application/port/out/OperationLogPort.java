package com.bank.credit.application.port.out;

import com.bank.credit.domain.model.CreditOperation;
import com.bank.credit.domain.model.OperationId;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.time.Instant;

/** Colección credit_operations: idempotencia y estado del registro en el historial. */
public interface OperationLogPort {

    Maybe<CreditOperation> find(OperationId operationId);

    Single<CreditOperation> save(CreditOperation operation);

    /** Operaciones con recorded = false creadas antes del instante (para el reintento). */
    Flowable<CreditOperation> findUnrecordedOlderThan(Instant threshold);
}

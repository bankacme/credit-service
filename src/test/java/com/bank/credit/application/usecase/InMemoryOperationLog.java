package com.bank.credit.application.usecase;

import com.bank.credit.application.port.out.OperationLogPort;
import com.bank.credit.domain.model.CreditOperation;
import com.bank.credit.domain.model.OperationId;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public class InMemoryOperationLog implements OperationLogPort {

    private final Map<OperationId, CreditOperation> byId = new LinkedHashMap<>();

    @Override
    public Maybe<CreditOperation> find(OperationId operationId) {
        CreditOperation found = byId.get(operationId);
        return found == null ? Maybe.empty() : Maybe.just(found);
    }

    @Override
    public Single<CreditOperation> save(CreditOperation operation) {
        byId.put(operation.operationId(), operation);
        return Single.just(operation);
    }

    @Override
    public Flowable<CreditOperation> findUnrecordedOlderThan(Instant threshold) {
        return Flowable.fromIterable(byId.values().stream()
                .filter(op -> !op.recorded() && op.createdAt().isBefore(threshold))
                .toList());
    }

    public CreditOperation get(String operationId) {
        return byId.get(new OperationId(operationId));
    }

    public int size() {
        return byId.size();
    }
}

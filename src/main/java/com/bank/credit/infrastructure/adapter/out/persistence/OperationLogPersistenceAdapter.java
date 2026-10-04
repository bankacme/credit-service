package com.bank.credit.infrastructure.adapter.out.persistence;

import com.bank.credit.application.port.out.OperationLogPort;
import com.bank.credit.domain.model.CreditOperation;
import com.bank.credit.domain.model.OperationId;
import com.bank.credit.infrastructure.mapper.CreditOperationDocumentMapper;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * credit_operations. {@code save} es un upsert por {@code _id}: aquí solo se usa para marcar una
 * operación ya existente como registrada; la primera escritura va por MongoUnitOfWorkAdapter.
 */
@Component
public class OperationLogPersistenceAdapter implements OperationLogPort {

    private final CreditOperationMongoRepository repository;
    private final CreditOperationDocumentMapper mapper;

    public OperationLogPersistenceAdapter(CreditOperationMongoRepository repository,
                                          CreditOperationDocumentMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Maybe<CreditOperation> find(OperationId operationId) {
        return repository.findById(operationId.value()).map(mapper::toDomain);
    }

    @Override
    public Single<CreditOperation> save(CreditOperation operation) {
        return repository.save(mapper.toDocument(operation)).map(mapper::toDomain);
    }

    @Override
    public Flowable<CreditOperation> findUnrecordedOlderThan(Instant threshold) {
        return repository.findByRecordedFalseAndCreatedAtBefore(threshold).map(mapper::toDomain);
    }
}

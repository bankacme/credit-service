package com.bank.credit.infrastructure.adapter.out.persistence;

import io.reactivex.rxjava3.core.Flowable;
import java.time.Instant;
import org.springframework.data.repository.reactive.RxJava3CrudRepository;

public interface CreditOperationMongoRepository extends RxJava3CrudRepository<CreditOperationDocument, String> {

    Flowable<CreditOperationDocument> findByRecordedFalseAndCreatedAtBefore(Instant threshold);
}

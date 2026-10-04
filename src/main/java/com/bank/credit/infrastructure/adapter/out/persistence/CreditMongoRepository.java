package com.bank.credit.infrastructure.adapter.out.persistence;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import java.time.LocalDate;
import java.util.Collection;
import org.springframework.data.repository.reactive.RxJava3CrudRepository;

/** Solo consultas derivadas (data-model §4), sin @Query. */
public interface CreditMongoRepository extends RxJava3CrudRepository<CreditDocument, String> {

    Flowable<CreditDocument> findByCustomerId(String customerId);

    Single<Boolean> existsByCustomerIdAndStatusIn(String customerId, Collection<String> statuses);

    Single<Boolean> existsByCustomerIdAndStatus(String customerId, String status);

    Flowable<CreditDocument> findByStatusAndDueDateBefore(String status, LocalDate date);
}

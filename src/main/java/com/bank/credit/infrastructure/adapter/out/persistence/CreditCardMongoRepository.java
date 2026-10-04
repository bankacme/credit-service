package com.bank.credit.infrastructure.adapter.out.persistence;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import java.time.LocalDate;
import org.springframework.data.repository.reactive.RxJava3CrudRepository;

public interface CreditCardMongoRepository extends RxJava3CrudRepository<CreditCardDocument, String> {

    Flowable<CreditCardDocument> findByCustomerId(String customerId);

    Single<Boolean> existsByCustomerIdAndStatus(String customerId, String status);

    Flowable<CreditCardDocument> findByStatusAndPaymentDueDateBefore(String status, LocalDate date);
}

package com.bank.credit.application.port.out;

import com.bank.credit.application.port.in.CreditFilter;
import com.bank.credit.domain.model.Credit;
import com.bank.credit.domain.model.CreditId;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.time.LocalDate;

/**
 * Créditos. {@code save} aplica control optimista por {@code version}: ante un conflicto falla con
 * BusinessRuleViolationException CONCURRENT_MODIFICATION (409), y ante el índice único parcial de
 * créditos personales no pagados, con PERSONAL_CREDIT_LIMIT_REACHED (422).
 */
public interface CreditRepositoryPort {

    Single<Credit> save(Credit credit);

    Maybe<Credit> findById(CreditId id);

    Flowable<Credit> findAll(CreditFilter filter);

    /** ¿Tiene el cliente algún crédito ACTIVE u OVERDUE? (regla 3). */
    Single<Boolean> existsUnpaidByCustomer(String customerId);

    /** Candidatos a vencer: ACTIVE con dueDate anterior a la fecha. */
    Flowable<Credit> findActiveDueBefore(LocalDate date);
}

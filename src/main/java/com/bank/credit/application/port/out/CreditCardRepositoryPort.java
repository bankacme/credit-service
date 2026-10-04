package com.bank.credit.application.port.out;

import com.bank.credit.application.port.in.CreditCardFilter;
import com.bank.credit.domain.model.CreditCard;
import com.bank.credit.domain.model.CreditCardId;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.time.LocalDate;

/**
 * Tarjetas. {@code save} falla con CardNumberCollisionException si el número ya existe y con
 * CONCURRENT_MODIFICATION ante un conflicto de versión.
 */
public interface CreditCardRepositoryPort {

    Single<CreditCard> save(CreditCard card);

    Maybe<CreditCard> findById(CreditCardId id);

    Flowable<CreditCard> findAll(CreditCardFilter filter);

    /** Candidatas a vencer: ACTIVE con paymentDueDate anterior a la fecha. */
    Flowable<CreditCard> findActiveDueBefore(LocalDate date);
}

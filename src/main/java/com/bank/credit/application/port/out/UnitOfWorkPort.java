package com.bank.credit.application.port.out;

import com.bank.credit.domain.model.Credit;
import com.bank.credit.domain.model.CreditCard;
import com.bank.credit.domain.model.CreditOperation;
import io.reactivex.rxjava3.core.Single;

/**
 * El producto y su operación se guardan juntos en una transacción de Mongo (data-model 3.3). Dos
 * métodos concretos en vez del {@code inTransaction(Single)} genérico de la ficha, mismo criterio
 * que account-service y transaction-service: la transacción vive entera en el adaptador.
 */
public interface UnitOfWorkPort {

    Single<Credit> saveCreditAndOperation(Credit credit, CreditOperation operation);

    Single<CreditCard> saveCardAndOperation(CreditCard card, CreditOperation operation);
}

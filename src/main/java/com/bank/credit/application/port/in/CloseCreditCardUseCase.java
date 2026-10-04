package com.bank.credit.application.port.in;

import com.bank.credit.domain.model.CreditCardId;
import io.reactivex.rxjava3.core.Completable;

public interface CloseCreditCardUseCase {

    Completable execute(CreditCardId id);
}

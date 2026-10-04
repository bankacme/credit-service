package com.bank.credit.application.port.in;

import com.bank.credit.domain.model.CreditId;
import io.reactivex.rxjava3.core.Completable;

public interface CloseCreditUseCase {

    Completable execute(CreditId id);
}

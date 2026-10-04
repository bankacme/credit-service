package com.bank.credit.application.port.in;

import com.bank.credit.domain.model.Credit;
import io.reactivex.rxjava3.core.Flowable;

public interface FindCreditsUseCase {

    Flowable<Credit> execute(CreditFilter filter);
}

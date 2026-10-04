package com.bank.credit.application.port.in;

import com.bank.credit.domain.model.Credit;
import com.bank.credit.domain.model.CreditId;
import io.reactivex.rxjava3.core.Single;

public interface FindCreditUseCase {

    Single<Credit> execute(CreditId id);
}

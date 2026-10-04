package com.bank.credit.application.port.in;

import com.bank.credit.application.view.CardBalanceView;
import com.bank.credit.domain.model.CreditCardId;
import io.reactivex.rxjava3.core.Single;

public interface GetCreditCardBalanceUseCase {

    Single<CardBalanceView> execute(CreditCardId id);
}

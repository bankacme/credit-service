package com.bank.credit.application.port.in;

import com.bank.credit.domain.model.CreditCard;
import io.reactivex.rxjava3.core.Flowable;

public interface FindCreditCardsUseCase {

    Flowable<CreditCard> execute(CreditCardFilter filter);
}

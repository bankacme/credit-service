package com.bank.credit.application.port.in;

import com.bank.credit.domain.model.CreditCard;
import com.bank.credit.domain.model.CreditCardId;
import io.reactivex.rxjava3.core.Single;

public interface FindCreditCardUseCase {

    Single<CreditCard> execute(CreditCardId id);
}

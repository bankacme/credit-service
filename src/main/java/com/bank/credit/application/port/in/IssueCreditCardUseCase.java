package com.bank.credit.application.port.in;

import com.bank.credit.application.command.IssueCreditCardCommand;
import com.bank.credit.domain.model.CreditCard;
import io.reactivex.rxjava3.core.Single;

public interface IssueCreditCardUseCase {

    Single<CreditCard> execute(IssueCreditCardCommand command);
}

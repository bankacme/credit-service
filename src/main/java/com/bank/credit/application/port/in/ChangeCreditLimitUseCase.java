package com.bank.credit.application.port.in;

import com.bank.credit.application.command.ChangeLimitCommand;
import com.bank.credit.domain.model.CreditCard;
import io.reactivex.rxjava3.core.Single;

public interface ChangeCreditLimitUseCase {

    Single<CreditCard> execute(ChangeLimitCommand command);
}

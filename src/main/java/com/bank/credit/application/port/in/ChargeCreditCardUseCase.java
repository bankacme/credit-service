package com.bank.credit.application.port.in;

import com.bank.credit.application.command.ChargeCommand;
import com.bank.credit.domain.model.ChargeResult;
import io.reactivex.rxjava3.core.Single;

public interface ChargeCreditCardUseCase {

    Single<ChargeResult> execute(ChargeCommand command);
}

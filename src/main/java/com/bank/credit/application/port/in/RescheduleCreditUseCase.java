package com.bank.credit.application.port.in;

import com.bank.credit.application.command.RescheduleCreditCommand;
import com.bank.credit.domain.model.Credit;
import io.reactivex.rxjava3.core.Single;

public interface RescheduleCreditUseCase {

    Single<Credit> execute(RescheduleCreditCommand command);
}

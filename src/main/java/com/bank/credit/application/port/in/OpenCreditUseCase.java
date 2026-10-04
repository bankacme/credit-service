package com.bank.credit.application.port.in;

import com.bank.credit.application.command.OpenCreditCommand;
import com.bank.credit.domain.model.Credit;
import io.reactivex.rxjava3.core.Single;

public interface OpenCreditUseCase {

    Single<Credit> execute(OpenCreditCommand command);
}

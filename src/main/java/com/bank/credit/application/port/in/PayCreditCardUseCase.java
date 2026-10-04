package com.bank.credit.application.port.in;

import com.bank.credit.application.command.PaymentCommand;
import com.bank.credit.domain.model.PaymentResult;
import io.reactivex.rxjava3.core.Single;

public interface PayCreditCardUseCase {

    Single<PaymentResult> execute(PaymentCommand command);
}

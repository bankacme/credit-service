package com.bank.credit.application.usecase;

import com.bank.credit.application.port.in.FindCreditCardUseCase;
import com.bank.credit.application.port.out.CreditCardRepositoryPort;
import com.bank.credit.domain.exception.CreditCardNotFoundException;
import com.bank.credit.domain.model.CreditCard;
import com.bank.credit.domain.model.CreditCardId;
import io.reactivex.rxjava3.core.Single;

public class FindCreditCardUseCaseImpl implements FindCreditCardUseCase {

    private final CreditCardRepositoryPort cardRepositoryPort;

    public FindCreditCardUseCaseImpl(CreditCardRepositoryPort cardRepositoryPort) {
        this.cardRepositoryPort = cardRepositoryPort;
    }

    @Override
    public Single<CreditCard> execute(CreditCardId id) {
        return cardRepositoryPort.findById(id)
                .switchIfEmpty(Single.error(() -> new CreditCardNotFoundException(id.value())));
    }
}

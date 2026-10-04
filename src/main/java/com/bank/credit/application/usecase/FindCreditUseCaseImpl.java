package com.bank.credit.application.usecase;

import com.bank.credit.application.port.in.FindCreditUseCase;
import com.bank.credit.application.port.out.CreditRepositoryPort;
import com.bank.credit.domain.exception.CreditNotFoundException;
import com.bank.credit.domain.model.Credit;
import com.bank.credit.domain.model.CreditId;
import io.reactivex.rxjava3.core.Single;

public class FindCreditUseCaseImpl implements FindCreditUseCase {

    private final CreditRepositoryPort creditRepositoryPort;

    public FindCreditUseCaseImpl(CreditRepositoryPort creditRepositoryPort) {
        this.creditRepositoryPort = creditRepositoryPort;
    }

    @Override
    public Single<Credit> execute(CreditId id) {
        return creditRepositoryPort.findById(id)
                .switchIfEmpty(Single.error(() -> new CreditNotFoundException(id.value())));
    }
}

package com.bank.credit.application.usecase;

import com.bank.credit.application.port.in.CreditFilter;
import com.bank.credit.application.port.in.FindCreditsUseCase;
import com.bank.credit.application.port.out.CreditRepositoryPort;
import com.bank.credit.domain.model.Credit;
import io.reactivex.rxjava3.core.Flowable;

public class FindCreditsUseCaseImpl implements FindCreditsUseCase {

    private final CreditRepositoryPort creditRepositoryPort;

    public FindCreditsUseCaseImpl(CreditRepositoryPort creditRepositoryPort) {
        this.creditRepositoryPort = creditRepositoryPort;
    }

    @Override
    public Flowable<Credit> execute(CreditFilter filter) {
        return creditRepositoryPort.findAll(filter == null ? CreditFilter.all() : filter);
    }
}

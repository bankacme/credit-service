package com.bank.credit.application.usecase;

import com.bank.credit.application.port.in.CreditCardFilter;
import com.bank.credit.application.port.in.FindCreditCardsUseCase;
import com.bank.credit.application.port.out.CreditCardRepositoryPort;
import com.bank.credit.domain.model.CreditCard;
import io.reactivex.rxjava3.core.Flowable;

public class FindCreditCardsUseCaseImpl implements FindCreditCardsUseCase {

    private final CreditCardRepositoryPort cardRepositoryPort;

    public FindCreditCardsUseCaseImpl(CreditCardRepositoryPort cardRepositoryPort) {
        this.cardRepositoryPort = cardRepositoryPort;
    }

    @Override
    public Flowable<CreditCard> execute(CreditCardFilter filter) {
        return cardRepositoryPort.findAll(filter == null ? CreditCardFilter.all() : filter);
    }
}

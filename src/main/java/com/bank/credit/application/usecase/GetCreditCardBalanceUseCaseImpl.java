package com.bank.credit.application.usecase;

import com.bank.credit.application.port.in.GetCreditCardBalanceUseCase;
import com.bank.credit.application.port.out.CreditCardRepositoryPort;
import com.bank.credit.application.view.CardBalanceView;
import com.bank.credit.domain.exception.CreditCardNotFoundException;
import com.bank.credit.domain.model.CreditCardId;
import io.reactivex.rxjava3.core.Single;
import java.time.Clock;

public class GetCreditCardBalanceUseCaseImpl implements GetCreditCardBalanceUseCase {

    private final CreditCardRepositoryPort cardRepositoryPort;
    private final Clock clock;

    public GetCreditCardBalanceUseCaseImpl(CreditCardRepositoryPort cardRepositoryPort, Clock clock) {
        this.cardRepositoryPort = cardRepositoryPort;
        this.clock = clock;
    }

    @Override
    public Single<CardBalanceView> execute(CreditCardId id) {
        return cardRepositoryPort.findById(id)
                .switchIfEmpty(Single.error(() -> new CreditCardNotFoundException(id.value())))
                .map(card -> CardBalanceView.of(card, clock.instant()));
    }
}

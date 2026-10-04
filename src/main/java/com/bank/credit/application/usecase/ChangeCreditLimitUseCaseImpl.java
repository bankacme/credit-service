package com.bank.credit.application.usecase;

import com.bank.credit.application.command.ChangeLimitCommand;
import com.bank.credit.application.port.in.ChangeCreditLimitUseCase;
import com.bank.credit.application.port.out.CreditCardRepositoryPort;
import com.bank.credit.application.port.out.CreditEventPublisherPort;
import com.bank.credit.domain.event.CreditCardUpdated;
import com.bank.credit.domain.exception.CreditCardNotFoundException;
import com.bank.credit.domain.model.CreditCard;
import io.reactivex.rxjava3.core.Single;
import java.time.Clock;

/** PUT /credit-cards/{id}: cambiar la línea (regla 17). */
public class ChangeCreditLimitUseCaseImpl implements ChangeCreditLimitUseCase {

    private final CreditCardRepositoryPort cardRepositoryPort;
    private final CreditEventPublisherPort eventPublisherPort;
    private final Clock clock;

    public ChangeCreditLimitUseCaseImpl(CreditCardRepositoryPort cardRepositoryPort,
                                        CreditEventPublisherPort eventPublisherPort, Clock clock) {
        this.cardRepositoryPort = cardRepositoryPort;
        this.eventPublisherPort = eventPublisherPort;
        this.clock = clock;
    }

    @Override
    public Single<CreditCard> execute(ChangeLimitCommand command) {
        return cardRepositoryPort.findById(command.cardId())
                .switchIfEmpty(Single.error(() -> new CreditCardNotFoundException(command.cardId().value())))
                .map(card -> card.changeLimit(command.creditLimit(), clock))
                .flatMap(cardRepositoryPort::save)
                .flatMap(saved -> eventPublisherPort.publish(CreditCardUpdated.from(saved))
                        .andThen(Single.just(saved)));
    }
}

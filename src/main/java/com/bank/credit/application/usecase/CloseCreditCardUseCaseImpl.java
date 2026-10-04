package com.bank.credit.application.usecase;

import com.bank.credit.application.port.in.CloseCreditCardUseCase;
import com.bank.credit.application.port.out.CreditCardRepositoryPort;
import com.bank.credit.application.port.out.CreditEventPublisherPort;
import com.bank.credit.domain.event.CreditCardClosed;
import com.bank.credit.domain.exception.CreditCardNotFoundException;
import com.bank.credit.domain.model.CreditCard;
import com.bank.credit.domain.model.CreditCardId;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import java.time.Clock;

/** DELETE /credit-cards/{id}: baja lógica. Repetir sobre una ya cerrada no guarda ni publica nada. */
public class CloseCreditCardUseCaseImpl implements CloseCreditCardUseCase {

    private final CreditCardRepositoryPort cardRepositoryPort;
    private final CreditEventPublisherPort eventPublisherPort;
    private final Clock clock;

    public CloseCreditCardUseCaseImpl(CreditCardRepositoryPort cardRepositoryPort,
                                      CreditEventPublisherPort eventPublisherPort, Clock clock) {
        this.cardRepositoryPort = cardRepositoryPort;
        this.eventPublisherPort = eventPublisherPort;
        this.clock = clock;
    }

    @Override
    public Completable execute(CreditCardId id) {
        return cardRepositoryPort.findById(id)
                .switchIfEmpty(Single.error(() -> new CreditCardNotFoundException(id.value())))
                .flatMapCompletable(current -> {
                    CreditCard closed = current.close(clock);
                    if (closed == current) {
                        return Completable.complete();
                    }
                    return cardRepositoryPort.save(closed)
                            .flatMapCompletable(saved -> eventPublisherPort.publish(CreditCardClosed.from(saved)));
                });
    }
}

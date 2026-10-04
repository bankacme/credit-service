package com.bank.credit.application.usecase;

import com.bank.credit.application.port.in.CloseCreditUseCase;
import com.bank.credit.application.port.out.CreditEventPublisherPort;
import com.bank.credit.application.port.out.CreditRepositoryPort;
import com.bank.credit.domain.event.CreditClosed;
import com.bank.credit.domain.exception.CreditNotFoundException;
import com.bank.credit.domain.model.Credit;
import com.bank.credit.domain.model.CreditId;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import java.time.Clock;

/** DELETE /credits/{id}: baja lógica. Repetir sobre uno ya cerrado no guarda ni publica nada. */
public class CloseCreditUseCaseImpl implements CloseCreditUseCase {

    private final CreditRepositoryPort creditRepositoryPort;
    private final CreditEventPublisherPort eventPublisherPort;
    private final Clock clock;

    public CloseCreditUseCaseImpl(CreditRepositoryPort creditRepositoryPort,
                                  CreditEventPublisherPort eventPublisherPort, Clock clock) {
        this.creditRepositoryPort = creditRepositoryPort;
        this.eventPublisherPort = eventPublisherPort;
        this.clock = clock;
    }

    @Override
    public Completable execute(CreditId id) {
        return creditRepositoryPort.findById(id)
                .switchIfEmpty(Single.error(() -> new CreditNotFoundException(id.value())))
                .flatMapCompletable(current -> {
                    Credit closed = current.close(clock);
                    if (closed == current) {
                        return Completable.complete();
                    }
                    return creditRepositoryPort.save(closed)
                            .flatMapCompletable(saved -> eventPublisherPort.publish(CreditClosed.from(saved)));
                });
    }
}

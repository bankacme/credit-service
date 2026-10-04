package com.bank.credit.application.usecase;

import com.bank.credit.application.command.RescheduleCreditCommand;
import com.bank.credit.application.port.in.RescheduleCreditUseCase;
import com.bank.credit.application.port.out.CreditEventPublisherPort;
import com.bank.credit.application.port.out.CreditRepositoryPort;
import com.bank.credit.domain.event.CreditUpdated;
import com.bank.credit.domain.exception.CreditNotFoundException;
import com.bank.credit.domain.model.Credit;
import com.bank.credit.domain.model.CreditStatus;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import java.time.Clock;

/** PUT /credits/{id}. Si el crédito sale de OVERDUE, revisa si al cliente le queda deuda vencida. */
public class RescheduleCreditUseCaseImpl implements RescheduleCreditUseCase {

    private final CreditRepositoryPort creditRepositoryPort;
    private final CreditEventPublisherPort eventPublisherPort;
    private final OverdueClearance overdueClearance;
    private final boolean demoMode;
    private final Clock clock;

    public RescheduleCreditUseCaseImpl(CreditRepositoryPort creditRepositoryPort,
                                       CreditEventPublisherPort eventPublisherPort,
                                       OverdueClearance overdueClearance, boolean demoMode, Clock clock) {
        this.creditRepositoryPort = creditRepositoryPort;
        this.eventPublisherPort = eventPublisherPort;
        this.overdueClearance = overdueClearance;
        this.demoMode = demoMode;
        this.clock = clock;
    }

    @Override
    public Single<Credit> execute(RescheduleCreditCommand command) {
        return creditRepositoryPort.findById(command.creditId())
                .switchIfEmpty(Single.error(() -> new CreditNotFoundException(command.creditId().value())))
                .flatMap(current -> {
                    Credit rescheduled = current.reschedule(command.dueDate(), demoMode, clock);
                    boolean leftOverdue = current.status() == CreditStatus.OVERDUE
                            && rescheduled.status() != CreditStatus.OVERDUE;
                    return creditRepositoryPort.save(rescheduled)
                            .flatMap(saved -> eventPublisherPort.publish(CreditUpdated.from(saved))
                                    .andThen(leftOverdue
                                            ? overdueClearance.afterLeavingOverdue(saved.customerId())
                                            : Completable.complete())
                                    .andThen(Single.just(saved)));
                });
    }
}

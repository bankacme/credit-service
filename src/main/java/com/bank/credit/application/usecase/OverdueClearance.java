package com.bank.credit.application.usecase;

import com.bank.credit.application.port.out.CreditEventPublisherPort;
import com.bank.credit.application.port.out.OverdueQueryPort;
import com.bank.credit.domain.event.CustomerOverdueCleared;
import io.reactivex.rxjava3.core.Completable;
import java.time.Clock;

/**
 * Paso 9 de data-model 2.2: después de guardar un producto que salió de OVERDUE, si al cliente ya
 * no le queda ningún producto vencido se publica credit.overdue.cleared (un solo aviso por cliente).
 */
public class OverdueClearance {

    private final OverdueQueryPort overdueQueryPort;
    private final CreditEventPublisherPort eventPublisherPort;
    private final Clock clock;

    public OverdueClearance(OverdueQueryPort overdueQueryPort, CreditEventPublisherPort eventPublisherPort,
                            Clock clock) {
        this.overdueQueryPort = overdueQueryPort;
        this.eventPublisherPort = eventPublisherPort;
        this.clock = clock;
    }

    public Completable afterLeavingOverdue(String customerId) {
        return overdueQueryPort.existsOverdueByCustomer(customerId)
                .flatMapCompletable(stillOverdue -> stillOverdue
                        ? Completable.complete()
                        : eventPublisherPort.publish(new CustomerOverdueCleared(customerId, clock.instant())));
    }
}

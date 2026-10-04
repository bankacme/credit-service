package com.bank.credit.application.port.out;

import com.bank.credit.domain.event.CreditDomainEvent;
import io.reactivex.rxjava3.core.Completable;

/** No-op en P1/P2, Kafka en P3. */
public interface CreditEventPublisherPort {

    Completable publish(CreditDomainEvent event);
}

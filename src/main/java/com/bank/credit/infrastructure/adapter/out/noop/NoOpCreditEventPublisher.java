package com.bank.credit.infrastructure.adapter.out.noop;

import com.bank.credit.application.port.out.CreditEventPublisherPort;
import com.bank.credit.domain.event.CreditDomainEvent;
import io.reactivex.rxjava3.core.Completable;
import org.springframework.stereotype.Component;

/**
 * P1/P2: nadie consume todavía los eventos de este servicio, así que publicar siempre completa.
 * Lo reemplaza un productor de Kafka en P3. Simplificación permanente durante P1/P2, igual que en
 * account-service y transaction-service.
 */
@Component
public class NoOpCreditEventPublisher implements CreditEventPublisherPort {

    @Override
    public Completable publish(CreditDomainEvent event) {
        return Completable.complete();
    }
}

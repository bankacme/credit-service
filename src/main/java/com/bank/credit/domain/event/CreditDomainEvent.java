package com.bank.credit.domain.event;

/** Eventos de la ficha §3.6. En P1/P2 los publica un no-op; en P3 van a Kafka (kafka-contract.md 6.5-6.8). */
public sealed interface CreditDomainEvent
        permits CreditOpened, CreditUpdated, CreditClosed, CreditCardIssued, CreditCardUpdated, CreditCardClosed,
        PaymentRegistered, ChargeRegistered, ProductBecameOverdue, CustomerOverdueCleared {
}

package com.bank.credit.domain.event;

import java.time.Instant;

/** credit.overdue.cleared: al cliente ya no le queda ningún producto vencido (kafka-contract 6.7). */
public record CustomerOverdueCleared(String customerId, Instant occurredAt) implements CreditDomainEvent {
}

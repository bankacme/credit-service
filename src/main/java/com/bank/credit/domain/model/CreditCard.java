package com.bank.credit.domain.model;

import com.bank.credit.domain.exception.BusinessRuleViolationException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Tarjeta de crédito con línea y monto usado. Inmutable, igual que {@link Credit}.
 *
 * <p>Estados: ACTIVE → OVERDUE (revisión de vencidos), OVERDUE → ACTIVE (pago total), ACTIVE →
 * CLOSED (baja lógica con usado 0). Una tarjeta OVERDUE sigue pudiendo consumir (regla 15).
 */
public record CreditCard(
        CreditCardId id,
        String customerId,
        OwnerType ownerType,
        CardNumber cardNumber,
        Money creditLimit,
        Money usedAmount,
        LocalDate paymentDueDate,
        CardStatus status,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /** Un pago aplicado: la tarjeta resultante, su resultado y si dejó de estar vencida. */
    public record PaymentOutcome(CreditCard card, PaymentResult result, boolean leftOverdue) {
    }

    /** Un consumo aplicado: la tarjeta resultante y su resultado. */
    public record ChargeOutcome(CreditCard card, ChargeResult result) {
    }

    public CreditCard {
        if (id == null || customerId == null || customerId.isBlank() || ownerType == null || cardNumber == null
                || creditLimit == null || usedAmount == null || status == null || createdAt == null
                || updatedAt == null) {
            throw new IllegalArgumentException("Required CreditCard fields must not be null/blank");
        }
        if (usedAmount.isGreaterThan(creditLimit)) {
            throw new IllegalArgumentException("usedAmount must not exceed creditLimit");
        }
        if (usedAmount.isZero() && paymentDueDate != null) {
            throw new IllegalArgumentException("A card with nothing used has no payment due date");
        }
    }

    /** Regla 7: línea > 0. Sin límite de tarjetas por cliente. */
    public static CreditCard issue(String customerId, OwnerType ownerType, CardNumber cardNumber, Money creditLimit,
                                   Clock clock) {
        requirePositive(creditLimit);
        Instant now = clock.instant();
        return new CreditCard(CreditCardId.newId(), customerId, ownerType, cardNumber, creditLimit, Money.zero(),
                null, CardStatus.ACTIVE, 0L, now, now);
    }

    public Money availableCredit() {
        return creditLimit.minus(usedAmount);
    }

    /** Reglas 11 y 12 (data-model 2.3). La fecha de pago se fija solo al primer consumo con usado 0. */
    public ChargeOutcome charge(OperationId operationId, Money amount, int paymentTermDays, Clock clock) {
        requireNotClosed("charges");
        requirePositive(amount);
        if (amount.isGreaterThan(availableCredit())) {
            throw new BusinessRuleViolationException("CREDIT_LIMIT_EXCEEDED",
                    "Charge " + amount.amount() + " exceeds the available credit " + availableCredit().amount());
        }
        LocalDate newPaymentDueDate = usedAmount.isZero()
                ? LocalDate.now(clock).plusDays(paymentTermDays)
                : paymentDueDate;
        CreditCard charged = new CreditCard(id, customerId, ownerType, cardNumber, creditLimit,
                usedAmount.plus(amount), newPaymentDueDate, status, version, createdAt, clock.instant());
        ChargeResult result = new ChargeResult(operationId, id.value(), amount, charged.usedAmount(),
                charged.availableCredit(), newPaymentDueDate, status);
        return new ChargeOutcome(charged, result);
    }

    /** Reglas 8, 10 y 14 (data-model 2.2): con usado 0 se borra la fecha de pago y OVERDUE vuelve a ACTIVE. */
    public PaymentOutcome registerPayment(OperationId operationId, Money amount, String payerCustomerId,
                                          Clock clock) {
        requireNotClosed("payments");
        requirePositive(amount);
        if (amount.isGreaterThan(usedAmount)) {
            throw new BusinessRuleViolationException("OVERPAYMENT",
                    "Payment " + amount.amount() + " exceeds the used amount " + usedAmount.amount());
        }
        Money newUsed = usedAmount.minus(amount);
        boolean fullyPaid = newUsed.isZero();
        CardStatus newStatus = fullyPaid ? CardStatus.ACTIVE : status;
        CreditCard paid = new CreditCard(id, customerId, ownerType, cardNumber, creditLimit, newUsed,
                fullyPaid ? null : paymentDueDate, newStatus, version, createdAt, clock.instant());
        String thirdPartyPayer = payerCustomerId != null && !payerCustomerId.equals(customerId)
                ? payerCustomerId
                : null;
        PaymentResult result = new PaymentResult(operationId, ProductType.CREDIT_CARD, id.value(), amount, newUsed,
                newStatus.name(), thirdPartyPayer);
        return new PaymentOutcome(paid, result, status == CardStatus.OVERDUE && newStatus != CardStatus.OVERDUE);
    }

    /** Regla 13: vencida = fecha de pago pasada y algo usado. Vacío si no aplica (idempotente). */
    public Optional<CreditCard> markOverdueIfDue(LocalDate asOf, Clock clock) {
        if (status != CardStatus.ACTIVE || paymentDueDate == null || !paymentDueDate.isBefore(asOf)
                || usedAmount.isZero()) {
            return Optional.empty();
        }
        return Optional.of(new CreditCard(id, customerId, ownerType, cardNumber, creditLimit, usedAmount,
                paymentDueDate, CardStatus.OVERDUE, version, createdAt, clock.instant()));
    }

    /** Regla 17: la nueva línea no puede quedar por debajo de lo ya usado. */
    public CreditCard changeLimit(Money newLimit, Clock clock) {
        requireNotClosed("limit changes");
        requirePositive(newLimit);
        if (usedAmount.isGreaterThan(newLimit)) {
            throw new BusinessRuleViolationException("LIMIT_BELOW_USED_AMOUNT",
                    "New limit " + newLimit.amount() + " is below the used amount " + usedAmount.amount());
        }
        return new CreditCard(id, customerId, ownerType, cardNumber, newLimit, usedAmount, paymentDueDate, status,
                version, createdAt, clock.instant());
    }

    /** Regla 16: baja lógica solo con usado 0. Cerrar una ya cerrada no cambia nada. */
    public CreditCard close(Clock clock) {
        if (status == CardStatus.CLOSED) {
            return this;
        }
        if (!usedAmount.isZero()) {
            throw new BusinessRuleViolationException("NOT_CLOSABLE",
                    "Credit card " + id.value() + " still has " + usedAmount.amount() + " used");
        }
        return new CreditCard(id, customerId, ownerType, cardNumber, creditLimit, usedAmount, null,
                CardStatus.CLOSED, version, createdAt, clock.instant());
    }

    private void requireNotClosed(String what) {
        if (status == CardStatus.CLOSED) {
            throw new BusinessRuleViolationException("INVALID_STATE",
                    "Credit card " + id.value() + " is CLOSED and does not accept " + what);
        }
    }

    private static void requirePositive(Money amount) {
        if (amount == null || amount.isZero()) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
    }
}

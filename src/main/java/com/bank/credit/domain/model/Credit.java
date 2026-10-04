package com.bank.credit.domain.model;

import com.bank.credit.domain.exception.BusinessRuleViolationException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Crédito con un solo vencimiento y saldo pendiente (sin cuotas ni intereses). Inmutable: cada
 * operación devuelve un {@code Credit} nuevo; {@code version} lo mantiene el adaptador de Mongo.
 *
 * <p>Estados: ACTIVE → OVERDUE (revisión de vencidos), OVERDUE → ACTIVE (reprogramar a una fecha
 * ≥ hoy), ACTIVE/OVERDUE → PAID (pago total), PAID → CLOSED (baja lógica).
 */
public record Credit(
        CreditId id,
        String customerId,
        OwnerType ownerType,
        Money principalAmount,
        Money outstandingBalance,
        LocalDate dueDate,
        CreditStatus status,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /** Un pago aplicado: el crédito resultante, su resultado y si el crédito dejó de estar vencido. */
    public record PaymentOutcome(Credit credit, PaymentResult result, boolean leftOverdue) {
    }

    public Credit {
        if (id == null || customerId == null || customerId.isBlank() || ownerType == null || principalAmount == null
                || outstandingBalance == null || dueDate == null || status == null || createdAt == null
                || updatedAt == null) {
            throw new IllegalArgumentException("Required Credit fields must not be null/blank");
        }
    }

    /** Reglas 6 (monto > 0, vencimiento futuro salvo modo demo). La 1-5 las aplica AcquisitionPolicy. */
    public static Credit open(String customerId, OwnerType ownerType, Money amount, LocalDate dueDate,
                              boolean demoMode, Clock clock) {
        requirePositive(amount);
        requireValidDueDate(dueDate, demoMode, clock);
        Instant now = clock.instant();
        return new Credit(CreditId.newId(), customerId, ownerType, amount, amount, dueDate, CreditStatus.ACTIVE,
                0L, now, now);
    }

    /** Reglas 8, 10, 14 y paso 7 de data-model 2.2 (pagador tercero). */
    public PaymentOutcome registerPayment(OperationId operationId, Money amount, String payerCustomerId,
                                          Clock clock) {
        if (status == CreditStatus.CLOSED || status == CreditStatus.PAID) {
            throw new BusinessRuleViolationException("INVALID_STATE",
                    "Credit " + id.value() + " is " + status + " and does not accept payments");
        }
        requirePositive(amount);
        if (amount.isGreaterThan(outstandingBalance)) {
            throw new BusinessRuleViolationException("OVERPAYMENT",
                    "Payment " + amount.amount() + " exceeds the outstanding balance " + outstandingBalance.amount());
        }
        Money newBalance = outstandingBalance.minus(amount);
        CreditStatus newStatus = newBalance.isZero() ? CreditStatus.PAID : status;
        Credit paid = new Credit(id, customerId, ownerType, principalAmount, newBalance, dueDate, newStatus, version,
                createdAt, clock.instant());
        String thirdPartyPayer = payerCustomerId != null && !payerCustomerId.equals(customerId)
                ? payerCustomerId
                : null;
        PaymentResult result = new PaymentResult(operationId, ProductType.CREDIT, id.value(), amount, newBalance,
                newStatus.name(), thirdPartyPayer);
        return new PaymentOutcome(paid, result, status == CreditStatus.OVERDUE && newStatus != CreditStatus.OVERDUE);
    }

    /** Regla 13: vencido = fecha pasada y saldo pendiente. Vacío si no aplica (idempotente). */
    public Optional<Credit> markOverdueIfDue(LocalDate asOf, Clock clock) {
        if (status != CreditStatus.ACTIVE || !dueDate.isBefore(asOf) || outstandingBalance.isZero()) {
            return Optional.empty();
        }
        return Optional.of(new Credit(id, customerId, ownerType, principalAmount, outstandingBalance, dueDate,
                CreditStatus.OVERDUE, version, createdAt, clock.instant()));
    }

    /**
     * Data-model 2.4: solo ACTIVE u OVERDUE; fecha futura salvo modo demo. Un OVERDUE vuelve a ACTIVE
     * si la nueva fecha es ≥ hoy; en modo demo, a una fecha pasada, conserva su estado.
     */
    public Credit reschedule(LocalDate newDueDate, boolean demoMode, Clock clock) {
        if (status != CreditStatus.ACTIVE && status != CreditStatus.OVERDUE) {
            throw new BusinessRuleViolationException("INVALID_STATE",
                    "Credit " + id.value() + " is " + status + " and cannot be rescheduled");
        }
        requireValidDueDate(newDueDate, demoMode, clock);
        LocalDate today = LocalDate.now(clock);
        CreditStatus newStatus = status == CreditStatus.OVERDUE && !newDueDate.isBefore(today)
                ? CreditStatus.ACTIVE
                : status;
        return new Credit(id, customerId, ownerType, principalAmount, outstandingBalance, newDueDate, newStatus,
                version, createdAt, clock.instant());
    }

    /** Regla 16: baja lógica solo con saldo 0. Cerrar uno ya cerrado no cambia nada. */
    public Credit close(Clock clock) {
        if (status == CreditStatus.CLOSED) {
            return this;
        }
        if (status != CreditStatus.PAID) {
            throw new BusinessRuleViolationException("NOT_CLOSABLE",
                    "Credit " + id.value() + " still has an outstanding balance of " + outstandingBalance.amount());
        }
        return new Credit(id, customerId, ownerType, principalAmount, outstandingBalance, dueDate,
                CreditStatus.CLOSED, version, createdAt, clock.instant());
    }

    /** ACTIVE u OVERDUE: cuenta para el límite de un crédito personal (regla 3). */
    public boolean isUnpaid() {
        return status == CreditStatus.ACTIVE || status == CreditStatus.OVERDUE;
    }

    private static void requirePositive(Money amount) {
        if (amount == null || amount.isZero()) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
    }

    private static void requireValidDueDate(LocalDate dueDate, boolean demoMode, Clock clock) {
        if (dueDate == null) {
            throw new IllegalArgumentException("dueDate is required");
        }
        if (!demoMode && !dueDate.isAfter(LocalDate.now(clock))) {
            throw new BusinessRuleViolationException("INVALID_DUE_DATE",
                    "Due date " + dueDate + " must be after today");
        }
    }
}

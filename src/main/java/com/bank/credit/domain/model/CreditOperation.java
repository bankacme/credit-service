package com.bank.credit.domain.model;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Un pago o consumo ya aplicado (colección {@code credit_operations}, {@code _id} = operationId).
 * Da la idempotencia (regla 18) y guarda si ya se registró en el historial de transaction-service
 * ({@code recorded}, data-model 2.6). Solo se guardan operaciones aplicadas: un rechazo no deja rastro.
 */
public record CreditOperation(
        OperationId operationId,
        OperationType type,
        ProductType productType,
        String productId,
        String customerId,
        String payerCustomerId,
        Money amount,
        String description,
        Money resultingBalance,
        String status,
        Money availableCredit,
        LocalDate paymentDueDate,
        boolean recorded,
        Instant recordedAt,
        Instant occurredAt,
        Instant createdAt) {

    public CreditOperation {
        if (operationId == null || type == null || productType == null || productId == null || customerId == null
                || amount == null || resultingBalance == null || status == null || occurredAt == null
                || createdAt == null) {
            throw new IllegalArgumentException("Required CreditOperation fields must not be null");
        }
        if (type == OperationType.CHARGE && productType != ProductType.CREDIT_CARD) {
            throw new IllegalArgumentException("Only a credit card accepts charges");
        }
    }

    /** Pago aplicado, todavía sin registrar en el historial. {@code customerId} = dueño del producto. */
    public static CreditOperation ofPayment(PaymentResult result, String customerId, Instant now) {
        return new CreditOperation(result.operationId(), OperationType.PAYMENT, result.productType(),
                result.productId(), customerId, result.payerCustomerId(), result.amount(), null,
                result.resultingBalance(), result.status(), null, null, false, null, now, now);
    }

    /** Consumo aplicado, todavía sin registrar en el historial. */
    public static CreditOperation ofCharge(ChargeResult result, String customerId, String description,
                                           Instant now) {
        return new CreditOperation(result.operationId(), OperationType.CHARGE, ProductType.CREDIT_CARD,
                result.cardId(), customerId, null, result.amount(), description, result.usedAmount(),
                result.status().name(), result.availableCredit(), result.paymentDueDate(), false, null, now, now);
    }

    /** Mismo operationId: solo es una repetición si es el mismo producto, tipo y monto (si no, 409). */
    public boolean matches(String candidateProductId, OperationType candidateType, Money candidateAmount) {
        return productId.equals(candidateProductId) && type == candidateType && amount.equals(candidateAmount);
    }

    public CreditOperation markRecorded(Instant now) {
        return new CreditOperation(operationId, type, productType, productId, customerId, payerCustomerId, amount,
                description, resultingBalance, status, availableCredit, paymentDueDate, true, now, occurredAt,
                createdAt);
    }

    /** El resultado original, para responder igual a una repetición. */
    public PaymentResult toPaymentResult() {
        if (type != OperationType.PAYMENT) {
            throw new IllegalStateException("Operation " + operationId.value() + " is a " + type);
        }
        return new PaymentResult(operationId, productType, productId, amount, resultingBalance, status,
                payerCustomerId);
    }

    public ChargeResult toChargeResult() {
        if (type != OperationType.CHARGE) {
            throw new IllegalStateException("Operation " + operationId.value() + " is a " + type);
        }
        return new ChargeResult(operationId, productId, amount, resultingBalance, availableCredit, paymentDueDate,
                CardStatus.valueOf(status));
    }
}

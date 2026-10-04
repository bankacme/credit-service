package com.bank.credit.domain.model;

/**
 * Resultado inmutable de un pago (crédito o tarjeta). {@code resultingBalance} es el saldo pendiente
 * del crédito o el monto usado de la tarjeta tras el pago; {@code status}, el estado del producto
 * tras el pago (nombre de {@link CreditStatus} o de {@link CardStatus}). {@code payerCustomerId}
 * solo viene informado si pagó un tercero (regla 9 de la ficha, paso 7 de data-model 2.2).
 */
public record PaymentResult(
        OperationId operationId,
        ProductType productType,
        String productId,
        Money amount,
        Money resultingBalance,
        String status,
        String payerCustomerId) {

    public PaymentResult {
        if (operationId == null || productType == null || productId == null || amount == null
                || resultingBalance == null || status == null) {
            throw new IllegalArgumentException("Required PaymentResult fields must not be null");
        }
    }
}

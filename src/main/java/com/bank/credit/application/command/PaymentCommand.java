package com.bank.credit.application.command;

import com.bank.credit.domain.model.Money;
import com.bank.credit.domain.model.OperationId;

/** Pago de un crédito o de una tarjeta. {@code payerCustomerId} es opcional (pago de tercero). */
public record PaymentCommand(String productId, OperationId operationId, Money amount, String payerCustomerId) {
}

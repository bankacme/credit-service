package com.bank.credit.application.command;

import com.bank.credit.domain.model.CreditCardId;
import com.bank.credit.domain.model.Money;
import com.bank.credit.domain.model.OperationId;

public record ChargeCommand(CreditCardId cardId, OperationId operationId, Money amount, String description) {
}

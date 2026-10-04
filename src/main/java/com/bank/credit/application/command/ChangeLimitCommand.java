package com.bank.credit.application.command;

import com.bank.credit.domain.model.CreditCardId;
import com.bank.credit.domain.model.Money;

public record ChangeLimitCommand(CreditCardId cardId, Money creditLimit) {
}

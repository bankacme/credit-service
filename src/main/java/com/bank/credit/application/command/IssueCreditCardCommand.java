package com.bank.credit.application.command;

import com.bank.credit.domain.model.Money;

public record IssueCreditCardCommand(String customerId, Money creditLimit) {
}

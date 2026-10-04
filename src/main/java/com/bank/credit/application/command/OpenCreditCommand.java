package com.bank.credit.application.command;

import com.bank.credit.domain.model.Money;
import java.time.LocalDate;

public record OpenCreditCommand(String customerId, Money amount, LocalDate dueDate) {
}

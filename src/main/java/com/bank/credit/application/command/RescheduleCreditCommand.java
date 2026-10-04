package com.bank.credit.application.command;

import com.bank.credit.domain.model.CreditId;
import java.time.LocalDate;

public record RescheduleCreditCommand(CreditId creditId, LocalDate dueDate) {
}

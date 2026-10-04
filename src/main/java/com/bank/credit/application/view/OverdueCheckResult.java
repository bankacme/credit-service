package com.bank.credit.application.view;

import java.time.Instant;
import java.time.LocalDate;

/** Resultado de una revisión de deuda vencida (data-model 2.5). */
public record OverdueCheckResult(
        LocalDate asOf, Instant ranAt, int creditsMarked, int cardsMarked, int customersAffected) {
}

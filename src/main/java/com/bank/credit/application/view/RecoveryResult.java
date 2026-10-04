package com.bank.credit.application.view;

import java.time.Instant;

/** Resultado de un reintento de registros pendientes en el historial (data-model 2.6). */
public record RecoveryResult(Instant ranAt, int olderThanMinutes, int found, int recorded, int stillPending) {
}

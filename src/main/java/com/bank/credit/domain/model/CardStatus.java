package com.bank.credit.domain.model;

/** Una tarjeta pagada por completo vuelve a ACTIVE: no hay estado PAID en tarjetas. */
public enum CardStatus {
    ACTIVE,
    OVERDUE,
    CLOSED
}

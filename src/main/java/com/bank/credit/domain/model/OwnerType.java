package com.bank.credit.domain.model;

/** Tipo del producto (personal o empresarial). No lo elige el usuario: se deriva del tipo del cliente. */
public enum OwnerType {
    PERSONAL,
    BUSINESS;

    public static OwnerType of(CustomerType customerType) {
        return customerType == CustomerType.BUSINESS ? BUSINESS : PERSONAL;
    }
}

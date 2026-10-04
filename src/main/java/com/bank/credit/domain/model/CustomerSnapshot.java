package com.bank.credit.domain.model;

/** Lo que este servicio necesita saber del cliente (CustomerLookupPort): tipo y estado. */
public record CustomerSnapshot(String customerId, CustomerType type, String status) {

    public CustomerSnapshot {
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("customerId must not be blank");
        }
        if (type == null || status == null) {
            throw new IllegalArgumentException("type and status are required");
        }
    }

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }
}

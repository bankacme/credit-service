package com.bank.credit.domain.service.validation;

import com.bank.credit.domain.model.CustomerSnapshot;
import com.bank.credit.domain.model.ProductType;

/**
 * Datos ya obtenidos por el caso de uso (puertos) para decidir si el cliente puede adquirir un
 * producto. {@code customer} es null si customer-service respondió 404. {@code hasUnpaidCredit}
 * solo importa al pedir un crédito.
 */
public record AcquisitionContext(
        ProductType requestedProduct,
        String customerId,
        CustomerSnapshot customer,
        boolean hasUnpaidCredit,
        boolean hasOverdueDebt) {
}

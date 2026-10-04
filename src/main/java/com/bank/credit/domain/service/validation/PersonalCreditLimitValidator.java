package com.bank.credit.domain.service.validation;

import com.bank.credit.domain.exception.BusinessRuleViolationException;
import com.bank.credit.domain.model.CustomerType;
import com.bank.credit.domain.model.ProductType;

/**
 * Eslabón 4 (regla 3): un cliente PERSONAL solo puede tener un crédito no pagado (ACTIVE u OVERDUE).
 * BUSINESS no tiene tope, y las tarjetas no cuentan. El índice único parcial cubre la carrera.
 */
public class PersonalCreditLimitValidator implements AcquisitionValidator {

    @Override
    public void validate(AcquisitionContext context) {
        if (context.requestedProduct() == ProductType.CREDIT && context.customer().type() == CustomerType.PERSONAL
                && context.hasUnpaidCredit()) {
            throw new BusinessRuleViolationException("PERSONAL_CREDIT_LIMIT_REACHED",
                    "Customer " + context.customerId() + " already has an unpaid personal credit");
        }
    }
}

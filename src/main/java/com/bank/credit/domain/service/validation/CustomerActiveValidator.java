package com.bank.credit.domain.service.validation;

import com.bank.credit.domain.exception.BusinessRuleViolationException;

/** Eslabón 2: el cliente está ACTIVE. */
public class CustomerActiveValidator implements AcquisitionValidator {

    @Override
    public void validate(AcquisitionContext context) {
        if (!context.customer().isActive()) {
            throw new BusinessRuleViolationException("CUSTOMER_INACTIVE",
                    "Customer " + context.customerId() + " is not active");
        }
    }
}

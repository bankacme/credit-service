package com.bank.credit.domain.service.validation;

import com.bank.credit.domain.exception.BusinessRuleViolationException;

/** Eslabón 5 (regla 5): un cliente con algún producto OVERDUE no adquiere productos nuevos. */
public class OverdueDebtValidator implements AcquisitionValidator {

    @Override
    public void validate(AcquisitionContext context) {
        if (context.hasOverdueDebt()) {
            throw new BusinessRuleViolationException("OVERDUE_DEBT",
                    "Customer " + context.customerId() + " has overdue debt and cannot acquire new products");
        }
    }
}

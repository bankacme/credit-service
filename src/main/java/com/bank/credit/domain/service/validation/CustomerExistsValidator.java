package com.bank.credit.domain.service.validation;

import com.bank.credit.domain.exception.CustomerNotFoundException;

/** Eslabón 1: el cliente existe (404 CUSTOMER_NOT_FOUND). */
public class CustomerExistsValidator implements AcquisitionValidator {

    @Override
    public void validate(AcquisitionContext context) {
        if (context.customer() == null) {
            throw new CustomerNotFoundException(context.customerId());
        }
    }
}

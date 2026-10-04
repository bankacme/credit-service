package com.bank.credit.domain.service.validation;

/** Un eslabón de la cadena de AcquisitionPolicy: lanza si su regla no se cumple. */
public interface AcquisitionValidator {

    void validate(AcquisitionContext context);
}

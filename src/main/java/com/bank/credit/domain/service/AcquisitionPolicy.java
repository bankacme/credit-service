package com.bank.credit.domain.service;

import com.bank.credit.domain.model.OwnerType;
import com.bank.credit.domain.service.validation.AcquisitionContext;
import com.bank.credit.domain.service.validation.AcquisitionValidator;
import com.bank.credit.domain.service.validation.CustomerActiveValidator;
import com.bank.credit.domain.service.validation.CustomerExistsValidator;
import com.bank.credit.domain.service.validation.OverdueDebtValidator;
import com.bank.credit.domain.service.validation.PersonalCreditLimitValidator;
import java.util.List;

/**
 * Reglas 1, 2, 3 y 5 al adquirir un crédito o una tarjeta, como cadena de responsabilidad: la
 * primera que falla corta (orden de data-model 2.1). Pura: recibe los datos ya obtenidos. El
 * eslabón 3 (tipo del producto = tipo del cliente) no puede fallar: es el valor que devuelve. El
 * eslabón 6 (vencimiento futuro) lo valida {@code Credit.open}.
 */
public class AcquisitionPolicy {

    private final List<AcquisitionValidator> chain = List.of(
            new CustomerExistsValidator(),
            new CustomerActiveValidator(),
            new PersonalCreditLimitValidator(),
            new OverdueDebtValidator());

    public OwnerType evaluate(AcquisitionContext context) {
        chain.forEach(validator -> validator.validate(context));
        return OwnerType.of(context.customer().type());
    }
}

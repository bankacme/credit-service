package com.bank.credit.application.port.out;

import io.reactivex.rxjava3.core.Single;

/** ¿Tiene el cliente algún crédito o tarjeta OVERDUE? Consulta propia sobre ambas colecciones. */
public interface OverdueQueryPort {

    Single<Boolean> existsOverdueByCustomer(String customerId);
}

package com.bank.credit.application.port.out;

import com.bank.credit.domain.model.CustomerSnapshot;
import io.reactivex.rxjava3.core.Maybe;

/** REST + circuit breaker contra customer-service (P1/P2); read model en P3. Vacío = 404. */
public interface CustomerLookupPort {

    Maybe<CustomerSnapshot> findById(String customerId);
}

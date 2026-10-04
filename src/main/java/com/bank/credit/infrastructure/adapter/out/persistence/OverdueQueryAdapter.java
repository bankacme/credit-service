package com.bank.credit.infrastructure.adapter.out.persistence;

import com.bank.credit.application.port.out.OverdueQueryPort;
import com.bank.credit.domain.model.CardStatus;
import com.bank.credit.domain.model.CreditStatus;
import io.reactivex.rxjava3.core.Single;
import org.springframework.stereotype.Component;

/** ¿Algún crédito o tarjeta OVERDUE del cliente? Solo consulta las tarjetas si no hay créditos vencidos. */
@Component
public class OverdueQueryAdapter implements OverdueQueryPort {

    private final CreditMongoRepository creditRepository;
    private final CreditCardMongoRepository cardRepository;

    public OverdueQueryAdapter(CreditMongoRepository creditRepository, CreditCardMongoRepository cardRepository) {
        this.creditRepository = creditRepository;
        this.cardRepository = cardRepository;
    }

    @Override
    public Single<Boolean> existsOverdueByCustomer(String customerId) {
        return creditRepository.existsByCustomerIdAndStatus(customerId, CreditStatus.OVERDUE.name())
                .flatMap(creditOverdue -> creditOverdue
                        ? Single.just(true)
                        : cardRepository.existsByCustomerIdAndStatus(customerId, CardStatus.OVERDUE.name()));
    }
}

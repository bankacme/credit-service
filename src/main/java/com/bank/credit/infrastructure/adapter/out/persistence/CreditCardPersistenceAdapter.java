package com.bank.credit.infrastructure.adapter.out.persistence;

import com.bank.credit.application.port.in.CreditCardFilter;
import com.bank.credit.application.port.out.CreditCardRepositoryPort;
import com.bank.credit.domain.model.CardStatus;
import com.bank.credit.domain.model.CreditCard;
import com.bank.credit.domain.model.CreditCardId;
import com.bank.credit.infrastructure.mapper.CreditCardDocumentMapper;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
public class CreditCardPersistenceAdapter implements CreditCardRepositoryPort {

    private final CreditCardMongoRepository repository;
    private final CreditCardDocumentMapper mapper;

    public CreditCardPersistenceAdapter(CreditCardMongoRepository repository, CreditCardDocumentMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Single<CreditCard> save(CreditCard card) {
        return repository.save(mapper.toDocument(card))
                .map(mapper::toDomain)
                .onErrorResumeNext(error -> Single.error(PersistenceErrors.translate(error)));
    }

    @Override
    public Maybe<CreditCard> findById(CreditCardId id) {
        return repository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public Flowable<CreditCard> findAll(CreditCardFilter filter) {
        Flowable<CreditCardDocument> base = filter.customerId() != null
                ? repository.findByCustomerId(filter.customerId())
                : repository.findAll();
        return base.map(mapper::toDomain)
                .filter(card -> filter.status() == null || card.status() == filter.status());
    }

    @Override
    public Flowable<CreditCard> findActiveDueBefore(LocalDate date) {
        return repository.findByStatusAndPaymentDueDateBefore(CardStatus.ACTIVE.name(), date)
                .map(mapper::toDomain);
    }
}

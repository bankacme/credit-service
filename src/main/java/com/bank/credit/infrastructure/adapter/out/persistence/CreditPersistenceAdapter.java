package com.bank.credit.infrastructure.adapter.out.persistence;

import com.bank.credit.application.port.in.CreditFilter;
import com.bank.credit.application.port.out.CreditRepositoryPort;
import com.bank.credit.domain.model.Credit;
import com.bank.credit.domain.model.CreditId;
import com.bank.credit.domain.model.CreditStatus;
import com.bank.credit.infrastructure.mapper.CreditDocumentMapper;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CreditPersistenceAdapter implements CreditRepositoryPort {

    private static final List<String> UNPAID = List.of(CreditStatus.ACTIVE.name(), CreditStatus.OVERDUE.name());

    private final CreditMongoRepository repository;
    private final CreditDocumentMapper mapper;

    public CreditPersistenceAdapter(CreditMongoRepository repository, CreditDocumentMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Single<Credit> save(Credit credit) {
        return repository.save(mapper.toDocument(credit))
                .map(mapper::toDomain)
                .onErrorResumeNext(error -> Single.error(PersistenceErrors.translate(error)));
    }

    @Override
    public Maybe<Credit> findById(CreditId id) {
        return repository.findById(id.value()).map(mapper::toDomain);
    }

    /** Con customerId usa el índice (customerId, status); el resto de filtros se aplica en memoria. */
    @Override
    public Flowable<Credit> findAll(CreditFilter filter) {
        Flowable<CreditDocument> base = filter.customerId() != null
                ? repository.findByCustomerId(filter.customerId())
                : repository.findAll();
        return base.map(mapper::toDomain)
                .filter(credit -> filter.ownerType() == null || credit.ownerType() == filter.ownerType())
                .filter(credit -> filter.status() == null || credit.status() == filter.status());
    }

    @Override
    public Single<Boolean> existsUnpaidByCustomer(String customerId) {
        return repository.existsByCustomerIdAndStatusIn(customerId, UNPAID);
    }

    @Override
    public Flowable<Credit> findActiveDueBefore(LocalDate date) {
        return repository.findByStatusAndDueDateBefore(CreditStatus.ACTIVE.name(), date).map(mapper::toDomain);
    }
}

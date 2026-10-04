package com.bank.credit.application.usecase;

import com.bank.credit.application.port.in.CreditFilter;
import com.bank.credit.application.port.out.CreditRepositoryPort;
import com.bank.credit.domain.exception.BusinessRuleViolationException;
import com.bank.credit.domain.model.Credit;
import com.bank.credit.domain.model.CreditId;
import com.bank.credit.domain.model.CreditStatus;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/** Repositorio en memoria. {@code failNextSavesWithConflict} simula un conflicto de versión de Mongo. */
public class InMemoryCreditRepository implements CreditRepositoryPort {

    private final Map<CreditId, Credit> byId = new LinkedHashMap<>();
    private int conflictsToSimulate;
    private int saves;

    public InMemoryCreditRepository failNextSavesWithConflict(int times) {
        this.conflictsToSimulate = times;
        return this;
    }

    @Override
    public Single<Credit> save(Credit credit) {
        return Single.defer(() -> {
            if (conflictsToSimulate > 0) {
                conflictsToSimulate--;
                return Single.error(new BusinessRuleViolationException("CONCURRENT_MODIFICATION", "conflict"));
            }
            saves++;
            byId.put(credit.id(), credit);
            return Single.just(credit);
        });
    }

    @Override
    public Maybe<Credit> findById(CreditId id) {
        Credit found = byId.get(id);
        return found == null ? Maybe.empty() : Maybe.just(found);
    }

    @Override
    public Flowable<Credit> findAll(CreditFilter filter) {
        return Flowable.fromIterable(byId.values())
                .filter(c -> filter.customerId() == null || filter.customerId().equals(c.customerId()))
                .filter(c -> filter.ownerType() == null || filter.ownerType() == c.ownerType())
                .filter(c -> filter.status() == null || filter.status() == c.status());
    }

    @Override
    public Single<Boolean> existsUnpaidByCustomer(String customerId) {
        return Single.just(byId.values().stream()
                .anyMatch(c -> c.customerId().equals(customerId) && c.isUnpaid()));
    }

    @Override
    public Flowable<Credit> findActiveDueBefore(LocalDate date) {
        return Flowable.fromIterable(byId.values().stream()
                .filter(c -> c.status() == CreditStatus.ACTIVE && c.dueDate().isBefore(date))
                .toList());
    }

    public Credit get(CreditId id) {
        return byId.get(id);
    }

    public int saves() {
        return saves;
    }
}

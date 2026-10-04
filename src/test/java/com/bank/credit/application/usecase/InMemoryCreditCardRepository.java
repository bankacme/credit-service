package com.bank.credit.application.usecase;

import com.bank.credit.application.port.in.CreditCardFilter;
import com.bank.credit.application.port.out.CreditCardRepositoryPort;
import com.bank.credit.domain.exception.BusinessRuleViolationException;
import com.bank.credit.domain.exception.CardNumberCollisionException;
import com.bank.credit.domain.model.CardStatus;
import com.bank.credit.domain.model.CreditCard;
import com.bank.credit.domain.model.CreditCardId;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/** En memoria, con la unicidad del número de tarjeta (uk_card_number) y conflictos simulados. */
public class InMemoryCreditCardRepository implements CreditCardRepositoryPort {

    private final Map<CreditCardId, CreditCard> byId = new LinkedHashMap<>();
    private int conflictsToSimulate;

    public InMemoryCreditCardRepository failNextSavesWithConflict(int times) {
        this.conflictsToSimulate = times;
        return this;
    }

    @Override
    public Single<CreditCard> save(CreditCard card) {
        return Single.defer(() -> {
            if (conflictsToSimulate > 0) {
                conflictsToSimulate--;
                return Single.error(new BusinessRuleViolationException("CONCURRENT_MODIFICATION", "conflict"));
            }
            boolean numberTaken = byId.values().stream()
                    .anyMatch(other -> !other.id().equals(card.id()) && other.cardNumber().equals(card.cardNumber()));
            if (numberTaken) {
                return Single.error(new CardNumberCollisionException());
            }
            byId.put(card.id(), card);
            return Single.just(card);
        });
    }

    @Override
    public Maybe<CreditCard> findById(CreditCardId id) {
        CreditCard found = byId.get(id);
        return found == null ? Maybe.empty() : Maybe.just(found);
    }

    @Override
    public Flowable<CreditCard> findAll(CreditCardFilter filter) {
        return Flowable.fromIterable(byId.values())
                .filter(c -> filter.customerId() == null || filter.customerId().equals(c.customerId()))
                .filter(c -> filter.status() == null || filter.status() == c.status());
    }

    @Override
    public Flowable<CreditCard> findActiveDueBefore(LocalDate date) {
        return Flowable.fromIterable(byId.values().stream()
                .filter(c -> c.status() == CardStatus.ACTIVE && c.paymentDueDate() != null
                        && c.paymentDueDate().isBefore(date))
                .toList());
    }

    public CreditCard get(CreditCardId id) {
        return byId.get(id);
    }

    public int size() {
        return byId.size();
    }
}

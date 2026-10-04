package com.bank.credit.application.usecase;

import com.bank.credit.application.port.out.CreditEventPublisherPort;
import com.bank.credit.application.port.out.CustomerLookupPort;
import com.bank.credit.application.port.out.MovementRecorderPort;
import com.bank.credit.application.port.out.OverdueQueryPort;
import com.bank.credit.application.port.out.UnitOfWorkPort;
import com.bank.credit.application.port.in.CreditCardFilter;
import com.bank.credit.application.port.in.CreditFilter;
import com.bank.credit.domain.event.CreditDomainEvent;
import com.bank.credit.domain.model.CardStatus;
import com.bank.credit.domain.model.Credit;
import com.bank.credit.domain.model.CreditCard;
import com.bank.credit.domain.model.CreditOperation;
import com.bank.credit.domain.model.CreditStatus;
import com.bank.credit.domain.model.CustomerSnapshot;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TestAdapters {

    private TestAdapters() {
    }

    public static final class StubCustomerLookupPort implements CustomerLookupPort {
        private final Map<String, CustomerSnapshot> byId = new HashMap<>();

        public StubCustomerLookupPort with(CustomerSnapshot customer) {
            byId.put(customer.customerId(), customer);
            return this;
        }

        @Override
        public Maybe<CustomerSnapshot> findById(String customerId) {
            CustomerSnapshot found = byId.get(customerId);
            return found == null ? Maybe.empty() : Maybe.just(found);
        }
    }

    /** Calcula la deuda vencida sobre los dos repositorios en memoria, como el adaptador de Mongo. */
    public static final class RepositoryOverdueQueryPort implements OverdueQueryPort {
        private final InMemoryCreditRepository credits;
        private final InMemoryCreditCardRepository cards;

        public RepositoryOverdueQueryPort(InMemoryCreditRepository credits, InMemoryCreditCardRepository cards) {
            this.credits = credits;
            this.cards = cards;
        }

        @Override
        public Single<Boolean> existsOverdueByCustomer(String customerId) {
            return credits.findAll(new CreditFilter(customerId, null, CreditStatus.OVERDUE)).isEmpty()
                    .flatMap(noCredits -> cards.findAll(new CreditCardFilter(customerId, CardStatus.OVERDUE))
                            .isEmpty().map(noCards -> !(noCredits && noCards)));
        }
    }

    public static final class PassthroughUnitOfWorkPort implements UnitOfWorkPort {
        private final InMemoryCreditRepository credits;
        private final InMemoryCreditCardRepository cards;
        private final InMemoryOperationLog operations;

        public PassthroughUnitOfWorkPort(InMemoryCreditRepository credits, InMemoryCreditCardRepository cards,
                                         InMemoryOperationLog operations) {
            this.credits = credits;
            this.cards = cards;
            this.operations = operations;
        }

        @Override
        public Single<Credit> saveCreditAndOperation(Credit credit, CreditOperation operation) {
            return credits.save(credit).flatMap(saved -> operations.save(operation).map(op -> saved));
        }

        @Override
        public Single<CreditCard> saveCardAndOperation(CreditCard card, CreditOperation operation) {
            return cards.save(card).flatMap(saved -> operations.save(operation).map(op -> saved));
        }
    }

    public static final class RecordingEventPublisherPort implements CreditEventPublisherPort {
        private final List<CreditDomainEvent> published = new ArrayList<>();

        @Override
        public Completable publish(CreditDomainEvent event) {
            published.add(event);
            return Completable.complete();
        }

        public List<CreditDomainEvent> published() {
            return published;
        }

        public <T extends CreditDomainEvent> List<T> ofType(Class<T> type) {
            return published.stream().filter(type::isInstance).map(type::cast).toList();
        }

        public void clear() {
            published.clear();
        }
    }

    /** Hace de transaction-service: registra lo que recibe o falla si está "caído". */
    public static final class FakeMovementRecorderPort implements MovementRecorderPort {
        private final List<CreditOperation> recorded = new ArrayList<>();
        private boolean down;

        public FakeMovementRecorderPort down(boolean isDown) {
            this.down = isDown;
            return this;
        }

        @Override
        public Completable record(CreditOperation operation) {
            return Completable.defer(() -> {
                if (down) {
                    return Completable.error(new RuntimeException("transaction-service is down"));
                }
                recorded.add(operation);
                return Completable.complete();
            });
        }

        public List<CreditOperation> recorded() {
            return recorded;
        }
    }
}

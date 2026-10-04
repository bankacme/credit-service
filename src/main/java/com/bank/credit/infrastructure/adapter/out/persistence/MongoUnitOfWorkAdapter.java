package com.bank.credit.infrastructure.adapter.out.persistence;

import com.bank.credit.application.port.out.UnitOfWorkPort;
import com.bank.credit.domain.exception.BusinessRuleViolationException;
import com.bank.credit.domain.model.Credit;
import com.bank.credit.domain.model.CreditCard;
import com.bank.credit.domain.model.CreditOperation;
import com.bank.credit.infrastructure.mapper.CreditCardDocumentMapper;
import com.bank.credit.infrastructure.mapper.CreditDocumentMapper;
import com.bank.credit.infrastructure.mapper.CreditOperationDocumentMapper;
import com.bank.credit.infrastructure.support.RxJavaReactorBridge;
import com.mongodb.MongoException;
import io.reactivex.rxjava3.core.Single;
import java.util.function.Supplier;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

/**
 * Producto + operación en una transacción de Mongo (data-model 3.3): se guardan los dos o ninguno.
 * Misma arquitectura que account-service: una sola cadena de Reactor dentro del
 * TransactionalOperator (sin RxJava en el medio) y reintento ante TransientTransactionError.
 *
 * <p>Diferencias propias de este servicio: la operación se escribe con {@code insert} (no
 * {@code save}), para que dos peticiones simultáneas con el mismo operationId no se pisen; y los
 * errores pasan por {@link PersistenceErrors}, así un conflicto de versión llega como
 * CONCURRENT_MODIFICATION.
 */
@Component
public class MongoUnitOfWorkAdapter implements UnitOfWorkPort {

    private static final int MAX_ATTEMPTS = 3;
    private static final String TRANSIENT_TRANSACTION_ERROR = "TransientTransactionError";

    private final ReactiveMongoTemplate mongoTemplate;
    private final TransactionalOperator transactionalOperator;
    private final CreditDocumentMapper creditMapper;
    private final CreditCardDocumentMapper cardMapper;
    private final CreditOperationDocumentMapper operationMapper;

    public MongoUnitOfWorkAdapter(ReactiveMongoTemplate mongoTemplate, TransactionalOperator transactionalOperator,
                                  CreditDocumentMapper creditMapper, CreditCardDocumentMapper cardMapper,
                                  CreditOperationDocumentMapper operationMapper) {
        this.mongoTemplate = mongoTemplate;
        this.transactionalOperator = transactionalOperator;
        this.creditMapper = creditMapper;
        this.cardMapper = cardMapper;
        this.operationMapper = operationMapper;
    }

    @Override
    public Single<Credit> saveCreditAndOperation(Credit credit, CreditOperation operation) {
        return inTransaction(() -> mongoTemplate.save(creditMapper.toDocument(credit))
                .flatMap(saved -> mongoTemplate.insert(operationMapper.toDocument(operation))
                        .thenReturn(creditMapper.toDomain(saved))), MAX_ATTEMPTS);
    }

    @Override
    public Single<CreditCard> saveCardAndOperation(CreditCard card, CreditOperation operation) {
        return inTransaction(() -> mongoTemplate.save(cardMapper.toDocument(card))
                .flatMap(saved -> mongoTemplate.insert(operationMapper.toDocument(operation))
                        .thenReturn(cardMapper.toDomain(saved))), MAX_ATTEMPTS);
    }

    private <T> Single<T> inTransaction(Supplier<Mono<T>> twoWrites, int attemptsLeft) {
        Mono<T> transactional = transactionalOperator.transactional(Mono.defer(twoWrites));
        return RxJavaReactorBridge.toSingle(transactional)
                .onErrorResumeNext(error -> {
                    if (!isTransientTransactionError(error)) {
                        return Single.error(PersistenceErrors.translate(error));
                    }
                    if (attemptsLeft > 1) {
                        return inTransaction(twoWrites, attemptsLeft - 1);
                    }
                    return Single.error(new BusinessRuleViolationException("CONCURRENT_MODIFICATION",
                            "Gave up after " + MAX_ATTEMPTS + " attempts due to a write conflict"));
                });
    }

    private static boolean isTransientTransactionError(Throwable error) {
        return error instanceof MongoException mongoError && mongoError.hasErrorLabel(TRANSIENT_TRANSACTION_ERROR);
    }

    /** Solo para la prueba de rollback: guarda el crédito y luego falla dentro de la transacción. */
    Single<Credit> saveCreditForcingFailureAfterward(Credit credit) {
        return RxJavaReactorBridge.toSingle(transactionalOperator.transactional(
                mongoTemplate.save(creditMapper.toDocument(credit))
                        .then(Mono.<Credit>error(new IllegalStateException("forced failure for the rollback test")))));
    }
}

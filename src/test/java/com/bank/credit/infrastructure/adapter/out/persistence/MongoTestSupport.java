package com.bank.credit.infrastructure.adapter.out.persistence;

import com.bank.credit.domain.exception.BusinessRuleViolationException;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Base de las pruebas contra Mongo real (bank_credit_test): limpia las tres colecciones antes y
 * después de cada prueba. Necesita el Mongo del docker-compose (replica set rs0) levantado.
 */
@SpringBootTest
abstract class MongoTestSupport {

    @Autowired
    protected CreditMongoRepository creditRepository;

    @Autowired
    protected CreditCardMongoRepository cardRepository;

    @Autowired
    protected CreditOperationMongoRepository operationRepository;

    @BeforeEach
    @AfterEach
    void cleanCollections() {
        creditRepository.deleteAll().blockingAwait();
        cardRepository.deleteAll().blockingAwait();
        operationRepository.deleteAll().blockingAwait();
    }

    /**
     * Contra Mongo real la operación es asíncrona: {@code test()} solo se suscribe. Hay que esperar
     * a que termine antes de afirmar nada (con los repositorios en memoria de R3 no hacía falta).
     */
    protected static <T> TestObserver<T> observe(Single<T> single) {
        return single.test().awaitDone(5, TimeUnit.SECONDS);
    }

    protected static boolean hasCode(Throwable error, String code) {
        return error instanceof BusinessRuleViolationException violation && violation.getErrorCode().equals(code);
    }
}

package com.bank.credit.infrastructure.config;

import com.bank.credit.infrastructure.adapter.out.persistence.CreditCardDocument;
import com.bank.credit.infrastructure.adapter.out.persistence.CreditDocument;
import com.bank.credit.infrastructure.adapter.out.persistence.CreditOperationDocument;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Índices de data-model §3, con nombre (los mensajes de DuplicateKeyException los usan para
 * traducir el error). Se crean al arrancar y se espera a que existan: así las reglas que dependen
 * de un índice único (regla 3, número de tarjeta) valen desde la primera petición.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CreditIndexInitializer implements ApplicationRunner {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final ReactiveMongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        Flux.concat(
                        create(CreditDocument.class, new Index().on("customerId", Sort.Direction.ASC)
                                .on("status", Sort.Direction.ASC).named("ix_credit_customer_status")),
                        create(CreditDocument.class, new Index().on("status", Sort.Direction.ASC)
                                .on("dueDate", Sort.Direction.ASC).named("ix_credit_status_due")),
                        create(CreditDocument.class, new Index().on("customerId", Sort.Direction.ASC).unique()
                                .partial(PartialIndexFilter.of(Criteria.where("unpaidPersonal").is(true)))
                                .named("uk_credit_personal_unpaid")),
                        create(CreditCardDocument.class, new Index().on("cardNumber", Sort.Direction.ASC).unique()
                                .named("uk_card_number")),
                        create(CreditCardDocument.class, new Index().on("customerId", Sort.Direction.ASC)
                                .on("status", Sort.Direction.ASC).named("ix_card_customer_status")),
                        create(CreditCardDocument.class, new Index().on("status", Sort.Direction.ASC)
                                .on("paymentDueDate", Sort.Direction.ASC).named("ix_card_status_due")),
                        create(CreditOperationDocument.class, new Index().on("recorded", Sort.Direction.ASC)
                                .on("createdAt", Sort.Direction.ASC).named("ix_op_recorded_created")))
                .then()
                .block(TIMEOUT);
    }

    private Mono<String> create(Class<?> documentType, Index index) {
        return mongoTemplate.indexOps(documentType).createIndex(index)
                .doOnNext(name -> log.info("Index '{}' ready on {}", name, documentType.getSimpleName()));
    }
}

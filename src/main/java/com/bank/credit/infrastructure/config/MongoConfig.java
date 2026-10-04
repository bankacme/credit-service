package com.bank.credit.infrastructure.config;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.bson.types.Decimal128;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.ReactiveMongoTransactionManager;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.transaction.reactive.TransactionalOperator;

/**
 * Conversiones globales (contracts/README.md): dinero como Decimal128 y LocalDate como texto
 * yyyy-MM-dd (compara bien como texto, también en los parámetros de las consultas derivadas). Y el
 * TransactionalOperator de la transacción producto + operación (requiere replica set).
 */
@Configuration
public class MongoConfig {

    @Bean
    public ReactiveMongoTransactionManager reactiveTransactionManager(ReactiveMongoDatabaseFactory factory) {
        return new ReactiveMongoTransactionManager(factory);
    }

    @Bean
    public TransactionalOperator transactionalOperator(ReactiveMongoTransactionManager txManager) {
        return TransactionalOperator.create(txManager);
    }

    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        return new MongoCustomConversions(List.of(
                BigDecimalToDecimal128Converter.INSTANCE,
                Decimal128ToBigDecimalConverter.INSTANCE,
                LocalDateToStringConverter.INSTANCE,
                StringToLocalDateConverter.INSTANCE));
    }

    @WritingConverter
    enum BigDecimalToDecimal128Converter implements Converter<BigDecimal, Decimal128> {
        INSTANCE;

        @Override
        public Decimal128 convert(BigDecimal source) {
            return new Decimal128(source);
        }
    }

    @ReadingConverter
    enum Decimal128ToBigDecimalConverter implements Converter<Decimal128, BigDecimal> {
        INSTANCE;

        @Override
        public BigDecimal convert(Decimal128 source) {
            return source.bigDecimalValue();
        }
    }

    @WritingConverter
    enum LocalDateToStringConverter implements Converter<LocalDate, String> {
        INSTANCE;

        @Override
        public String convert(LocalDate source) {
            return source.format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
    }

    @ReadingConverter
    enum StringToLocalDateConverter implements Converter<String, LocalDate> {
        INSTANCE;

        @Override
        public LocalDate convert(String source) {
            return LocalDate.parse(source, DateTimeFormatter.ISO_LOCAL_DATE);
        }
    }
}

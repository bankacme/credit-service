package com.bank.credit.infrastructure.fixture;

import com.bank.credit.domain.model.CardNumber;
import com.bank.credit.domain.model.Credit;
import com.bank.credit.domain.model.CreditCard;
import com.bank.credit.domain.model.CreditOperation;
import com.bank.credit.domain.model.Money;
import com.bank.credit.domain.model.OperationId;
import com.bank.credit.domain.model.OwnerType;
import com.bank.credit.domain.service.CardNumberGenerator;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Random;

/** Datos de prueba de la capa de infraestructura (reloj fijo, instantes truncados a ms como Mongo). */
public final class CreditFixtures {

    public static final LocalDate TODAY = LocalDate.of(2026, 10, 1);
    public static final Clock CLOCK = Clock.fixed(
            TODAY.atStartOfDay(ZoneOffset.UTC).toInstant().truncatedTo(ChronoUnit.MILLIS), ZoneOffset.UTC);

    private CreditFixtures() {
    }

    public static Credit credit(String customerId, OwnerType ownerType, String amount, LocalDate dueDate) {
        return Credit.open(customerId, ownerType, Money.of(amount), dueDate, true, CLOCK);
    }

    public static CreditCard card(String customerId, String limit) {
        return card(customerId, limit, new CardNumberGenerator().generate(new Random()));
    }

    public static CreditCard card(String customerId, String limit, CardNumber number) {
        return CreditCard.issue(customerId, OwnerType.PERSONAL, number, Money.of(limit), CLOCK);
    }

    public static CreditOperation paymentOf(Credit credit, String operationId, String amount, String payer) {
        return CreditOperation.ofPayment(credit.registerPayment(new OperationId(operationId), Money.of(amount),
                payer, CLOCK).result(), credit.customerId(), CLOCK.instant());
    }
}

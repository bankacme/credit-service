package com.bank.credit.application.view;

import com.bank.credit.domain.model.Credit;
import com.bank.credit.domain.model.CreditCard;
import com.bank.credit.domain.model.Money;
import com.bank.credit.domain.model.ProductType;
import java.time.LocalDate;

/**
 * Vista limitada para que un tercero pague un producto: cuánto se debe y para cuándo, sin el
 * resto de datos del dueño. {@code maskedNumber} solo para tarjetas.
 */
public record PaymentInfoView(
        ProductType productType, String productId, String maskedNumber, String status, Money amountDue,
        LocalDate dueDate) {

    public static PaymentInfoView of(Credit credit) {
        return new PaymentInfoView(ProductType.CREDIT, credit.id().value(), null, credit.status().name(),
                credit.outstandingBalance(), credit.dueDate());
    }

    public static PaymentInfoView of(CreditCard card) {
        return new PaymentInfoView(ProductType.CREDIT_CARD, card.id().value(), card.cardNumber().masked(),
                card.status().name(), card.usedAmount(), card.paymentDueDate());
    }
}

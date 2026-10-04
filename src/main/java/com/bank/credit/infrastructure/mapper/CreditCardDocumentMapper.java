package com.bank.credit.infrastructure.mapper;

import com.bank.credit.domain.model.CardNumber;
import com.bank.credit.domain.model.CardStatus;
import com.bank.credit.domain.model.CreditCard;
import com.bank.credit.domain.model.CreditCardId;
import com.bank.credit.domain.model.Money;
import com.bank.credit.domain.model.OwnerType;
import com.bank.credit.infrastructure.adapter.out.persistence.CreditCardDocument;
import org.springframework.stereotype.Component;

@Component
public class CreditCardDocumentMapper {

    public CreditCardDocument toDocument(CreditCard card) {
        return CreditCardDocument.builder()
                .id(card.id().value())
                .customerId(card.customerId())
                .ownerType(card.ownerType().name())
                .cardNumber(card.cardNumber().value())
                .creditLimit(card.creditLimit().amount())
                .usedAmount(card.usedAmount().amount())
                .paymentDueDate(card.paymentDueDate())
                .status(card.status().name())
                .version(card.version())
                .createdAt(card.createdAt())
                .updatedAt(card.updatedAt())
                .build();
    }

    public CreditCard toDomain(CreditCardDocument document) {
        return new CreditCard(
                new CreditCardId(document.getId()),
                document.getCustomerId(),
                OwnerType.valueOf(document.getOwnerType()),
                new CardNumber(document.getCardNumber()),
                Money.of(document.getCreditLimit()),
                Money.of(document.getUsedAmount()),
                document.getPaymentDueDate(),
                CardStatus.valueOf(document.getStatus()),
                document.getVersion(),
                document.getCreatedAt(),
                document.getUpdatedAt());
    }
}

package com.bank.credit.infrastructure.mapper;

import com.bank.credit.domain.model.CreditOperation;
import com.bank.credit.domain.model.Money;
import com.bank.credit.domain.model.OperationId;
import com.bank.credit.domain.model.OperationType;
import com.bank.credit.domain.model.ProductType;
import com.bank.credit.infrastructure.adapter.out.persistence.CreditOperationDocument;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class CreditOperationDocumentMapper {

    public CreditOperationDocument toDocument(CreditOperation operation) {
        return CreditOperationDocument.builder()
                .id(operation.operationId().value())
                .type(operation.type().name())
                .productType(operation.productType().name())
                .productId(operation.productId())
                .customerId(operation.customerId())
                .payerCustomerId(operation.payerCustomerId())
                .amount(operation.amount().amount())
                .description(operation.description())
                .resultingBalance(operation.resultingBalance().amount())
                .status(operation.status())
                .availableCredit(operation.availableCredit() == null ? null : operation.availableCredit().amount())
                .paymentDueDate(operation.paymentDueDate())
                .recorded(operation.recorded())
                .recordedAt(operation.recordedAt())
                .occurredAt(operation.occurredAt())
                .createdAt(operation.createdAt())
                .build();
    }

    public CreditOperation toDomain(CreditOperationDocument document) {
        return new CreditOperation(
                new OperationId(document.getId()),
                OperationType.valueOf(document.getType()),
                ProductType.valueOf(document.getProductType()),
                document.getProductId(),
                document.getCustomerId(),
                document.getPayerCustomerId(),
                Money.of(document.getAmount()),
                document.getDescription(),
                Money.of(document.getResultingBalance()),
                document.getStatus(),
                moneyOrNull(document.getAvailableCredit()),
                document.getPaymentDueDate(),
                document.isRecorded(),
                document.getRecordedAt(),
                document.getOccurredAt(),
                document.getCreatedAt());
    }

    private static Money moneyOrNull(BigDecimal amount) {
        return amount == null ? null : Money.of(amount);
    }
}

package com.bank.credit.infrastructure.mapper;

import com.bank.credit.domain.model.Credit;
import com.bank.credit.domain.model.CreditId;
import com.bank.credit.domain.model.CreditStatus;
import com.bank.credit.domain.model.Money;
import com.bank.credit.domain.model.OwnerType;
import com.bank.credit.infrastructure.adapter.out.persistence.CreditDocument;
import org.springframework.stereotype.Component;

/**
 * Credit ↔ CreditDocument. Escrito a mano (no MapStruct), mismo criterio que account-service y
 * transaction-service: los records del dominio validan en el constructor y el mapeo es corto.
 */
@Component
public class CreditDocumentMapper {

    public CreditDocument toDocument(Credit credit) {
        return CreditDocument.builder()
                .id(credit.id().value())
                .customerId(credit.customerId())
                .ownerType(credit.ownerType().name())
                .principalAmount(credit.principalAmount().amount())
                .outstandingBalance(credit.outstandingBalance().amount())
                .dueDate(credit.dueDate())
                .status(credit.status().name())
                .unpaidPersonal(isUnpaidPersonal(credit) ? Boolean.TRUE : null)
                .version(credit.version())
                .createdAt(credit.createdAt())
                .updatedAt(credit.updatedAt())
                .build();
    }

    public Credit toDomain(CreditDocument document) {
        return new Credit(
                new CreditId(document.getId()),
                document.getCustomerId(),
                OwnerType.valueOf(document.getOwnerType()),
                Money.of(document.getPrincipalAmount()),
                Money.of(document.getOutstandingBalance()),
                document.getDueDate(),
                CreditStatus.valueOf(document.getStatus()),
                document.getVersion(),
                document.getCreatedAt(),
                document.getUpdatedAt());
    }

    /** Campo derivado del índice parcial: crédito PERSONAL ACTIVE u OVERDUE. */
    static boolean isUnpaidPersonal(Credit credit) {
        return credit.ownerType() == OwnerType.PERSONAL && credit.isUnpaid();
    }
}

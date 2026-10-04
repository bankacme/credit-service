package com.bank.credit.infrastructure.adapter.out.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Colección credit_operations (data-model 3.3): {@code _id} = operationId, solo operaciones aplicadas. */
@Document("credit_operations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditOperationDocument {

    @Id
    private String id;

    private String type;

    private String productType;

    private String productId;

    private String customerId;

    private String payerCustomerId;

    private BigDecimal amount;

    private String description;

    private BigDecimal resultingBalance;

    private String status;

    private BigDecimal availableCredit;

    private LocalDate paymentDueDate;

    private boolean recorded;

    private Instant recordedAt;

    private Instant occurredAt;

    private Instant createdAt;
}

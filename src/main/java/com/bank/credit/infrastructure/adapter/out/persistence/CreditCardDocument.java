package com.bank.credit.infrastructure.adapter.out.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

/** Colección credit_cards (data-model 3.2). El número completo nunca se escribe en un log. */
@Document("credit_cards")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditCardDocument {

    @Id
    private String id;

    private String customerId;

    private String ownerType;

    @ToString.Exclude
    private String cardNumber;

    private BigDecimal creditLimit;

    private BigDecimal usedAmount;

    private LocalDate paymentDueDate;

    private String status;

    @Version
    private long version;

    private Instant createdAt;

    private Instant updatedAt;
}

package com.bank.credit.infrastructure.adapter.out.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Colección credits (data-model 3.1). {@code unpaidPersonal} es un campo derivado que mantiene el
 * mapper: true solo para un crédito PERSONAL ACTIVE u OVERDUE, y null (se omite) en los demás
 * casos; el índice único parcial uk_credit_personal_unpaid se apoya en él (regla 3 sin carreras).
 */
@Document("credits")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditDocument {

    @Id
    private String id;

    private String customerId;

    private String ownerType;

    private BigDecimal principalAmount;

    private BigDecimal outstandingBalance;

    private LocalDate dueDate;

    private String status;

    private Boolean unpaidPersonal;

    @Version
    private long version;

    private Instant createdAt;

    private Instant updatedAt;
}

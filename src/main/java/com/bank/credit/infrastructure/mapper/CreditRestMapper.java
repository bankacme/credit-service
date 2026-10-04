package com.bank.credit.infrastructure.mapper;

import com.bank.credit.application.command.ChangeLimitCommand;
import com.bank.credit.application.command.ChargeCommand;
import com.bank.credit.application.command.IssueCreditCardCommand;
import com.bank.credit.application.command.OpenCreditCommand;
import com.bank.credit.application.command.PaymentCommand;
import com.bank.credit.application.command.RescheduleCreditCommand;
import com.bank.credit.application.port.in.CreditCardFilter;
import com.bank.credit.application.port.in.CreditFilter;
import com.bank.credit.application.view.CardBalanceView;
import com.bank.credit.application.view.OverdueCheckResult;
import com.bank.credit.application.view.PaymentInfoView;
import com.bank.credit.application.view.RecoveryResult;
import com.bank.credit.domain.model.ChargeResult;
import com.bank.credit.domain.model.Credit;
import com.bank.credit.domain.model.CreditCard;
import com.bank.credit.domain.model.CreditCardId;
import com.bank.credit.domain.model.CreditId;
import com.bank.credit.domain.model.Money;
import com.bank.credit.domain.model.OperationId;
import com.bank.credit.domain.model.PaymentResult;
import com.bank.credit.infrastructure.adapter.in.rest.dto.CardBalance;
import com.bank.credit.infrastructure.adapter.in.rest.dto.CardStatus;
import com.bank.credit.infrastructure.adapter.in.rest.dto.ChangeLimitRequest;
import com.bank.credit.infrastructure.adapter.in.rest.dto.ChargeRequest;
import com.bank.credit.infrastructure.adapter.in.rest.dto.CreditStatus;
import com.bank.credit.infrastructure.adapter.in.rest.dto.IssueCreditCardRequest;
import com.bank.credit.infrastructure.adapter.in.rest.dto.OpenCreditRequest;
import com.bank.credit.infrastructure.adapter.in.rest.dto.OwnerType;
import com.bank.credit.infrastructure.adapter.in.rest.dto.PaymentInfo;
import com.bank.credit.infrastructure.adapter.in.rest.dto.PaymentRequest;
import com.bank.credit.infrastructure.adapter.in.rest.dto.ProductType;
import com.bank.credit.infrastructure.adapter.in.rest.dto.RescheduleCreditRequest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

/**
 * DTOs generados del contrato ↔ comandos, filtros, dominio y vistas. Escrito a mano, como los
 * mappers de persistencia. {@code cardNumber} nunca sale: solo {@code maskedNumber}; y
 * {@code availableCredit} se calcula (data-model §5).
 */
@Component
public class CreditRestMapper {

    // ---- requests → comandos y filtros ----

    public OpenCreditCommand toCommand(OpenCreditRequest request) {
        return new OpenCreditCommand(request.getCustomerId(), Money.of(request.getAmount()), request.getDueDate());
    }

    public RescheduleCreditCommand toCommand(String creditId, RescheduleCreditRequest request) {
        return new RescheduleCreditCommand(new CreditId(creditId), request.getDueDate());
    }

    public IssueCreditCardCommand toCommand(IssueCreditCardRequest request) {
        return new IssueCreditCardCommand(request.getCustomerId(), Money.of(request.getCreditLimit()));
    }

    public ChangeLimitCommand toCommand(String cardId, ChangeLimitRequest request) {
        return new ChangeLimitCommand(new CreditCardId(cardId), Money.of(request.getCreditLimit()));
    }

    public PaymentCommand toCommand(String productId, PaymentRequest request) {
        return new PaymentCommand(productId, new OperationId(request.getOperationId()),
                Money.of(request.getAmount()), request.getPayerCustomerId());
    }

    public ChargeCommand toCommand(String cardId, ChargeRequest request) {
        return new ChargeCommand(new CreditCardId(cardId), new OperationId(request.getOperationId()),
                Money.of(request.getAmount()), request.getDescription());
    }

    public CreditFilter toFilter(String customerId, OwnerType ownerType, CreditStatus status) {
        return new CreditFilter(customerId,
                ownerType == null ? null : com.bank.credit.domain.model.OwnerType.valueOf(ownerType.getValue()),
                status == null ? null : com.bank.credit.domain.model.CreditStatus.valueOf(status.getValue()));
    }

    public CreditCardFilter toFilter(String customerId, CardStatus status) {
        return new CreditCardFilter(customerId,
                status == null ? null : com.bank.credit.domain.model.CardStatus.valueOf(status.getValue()));
    }

    // ---- dominio y vistas → DTOs ----

    public com.bank.credit.infrastructure.adapter.in.rest.dto.Credit toDto(Credit credit) {
        com.bank.credit.infrastructure.adapter.in.rest.dto.Credit dto =
                new com.bank.credit.infrastructure.adapter.in.rest.dto.Credit();
        dto.setId(credit.id().value());
        dto.setCustomerId(credit.customerId());
        dto.setOwnerType(OwnerType.fromValue(credit.ownerType().name()));
        dto.setPrincipalAmount(credit.principalAmount().amount());
        dto.setOutstandingBalance(credit.outstandingBalance().amount());
        dto.setDueDate(credit.dueDate());
        dto.setStatus(CreditStatus.fromValue(credit.status().name()));
        dto.setCreatedAt(toOffset(credit.createdAt()));
        dto.setUpdatedAt(toOffset(credit.updatedAt()));
        return dto;
    }

    public com.bank.credit.infrastructure.adapter.in.rest.dto.CreditCard toDto(CreditCard card) {
        com.bank.credit.infrastructure.adapter.in.rest.dto.CreditCard dto =
                new com.bank.credit.infrastructure.adapter.in.rest.dto.CreditCard();
        dto.setId(card.id().value());
        dto.setCustomerId(card.customerId());
        dto.setOwnerType(OwnerType.fromValue(card.ownerType().name()));
        dto.setMaskedNumber(card.cardNumber().masked());
        dto.setCreditLimit(card.creditLimit().amount());
        dto.setUsedAmount(card.usedAmount().amount());
        dto.setAvailableCredit(card.availableCredit().amount());
        dto.setPaymentDueDate(card.paymentDueDate());
        dto.setStatus(CardStatus.fromValue(card.status().name()));
        dto.setCreatedAt(toOffset(card.createdAt()));
        dto.setUpdatedAt(toOffset(card.updatedAt()));
        return dto;
    }

    public com.bank.credit.infrastructure.adapter.in.rest.dto.PaymentResult toDto(PaymentResult result) {
        com.bank.credit.infrastructure.adapter.in.rest.dto.PaymentResult dto =
                new com.bank.credit.infrastructure.adapter.in.rest.dto.PaymentResult();
        dto.setOperationId(result.operationId().value());
        dto.setProductType(ProductType.fromValue(result.productType().name()));
        dto.setProductId(result.productId());
        dto.setAmount(result.amount().amount());
        dto.setResultingBalance(result.resultingBalance().amount());
        dto.setStatus(com.bank.credit.infrastructure.adapter.in.rest.dto.PaymentResult.StatusEnum
                .fromValue(result.status()));
        dto.setPayerCustomerId(result.payerCustomerId());
        return dto;
    }

    public com.bank.credit.infrastructure.adapter.in.rest.dto.ChargeResult toDto(ChargeResult result) {
        com.bank.credit.infrastructure.adapter.in.rest.dto.ChargeResult dto =
                new com.bank.credit.infrastructure.adapter.in.rest.dto.ChargeResult();
        dto.setOperationId(result.operationId().value());
        dto.setCardId(result.cardId());
        dto.setAmount(result.amount().amount());
        dto.setUsedAmount(result.usedAmount().amount());
        dto.setAvailableCredit(result.availableCredit().amount());
        dto.setPaymentDueDate(result.paymentDueDate());
        dto.setStatus(CardStatus.fromValue(result.status().name()));
        return dto;
    }

    public CardBalance toDto(CardBalanceView view) {
        CardBalance dto = new CardBalance();
        dto.setCardId(view.cardId());
        dto.setStatus(CardStatus.fromValue(view.status().name()));
        dto.setCreditLimit(view.creditLimit().amount());
        dto.setUsedAmount(view.usedAmount().amount());
        dto.setAvailableCredit(view.availableCredit().amount());
        dto.setPaymentDueDate(view.paymentDueDate());
        dto.setAsOf(toOffset(view.asOf()));
        return dto;
    }

    public PaymentInfo toDto(PaymentInfoView view) {
        PaymentInfo dto = new PaymentInfo();
        dto.setProductType(ProductType.fromValue(view.productType().name()));
        dto.setProductId(view.productId());
        dto.setMaskedNumber(view.maskedNumber());
        dto.setStatus(PaymentInfo.StatusEnum.fromValue(view.status()));
        dto.setAmountDue(view.amountDue().amount());
        dto.setDueDate(view.dueDate());
        return dto;
    }

    public com.bank.credit.infrastructure.adapter.in.rest.dto.OverdueCheckResult toDto(OverdueCheckResult result) {
        com.bank.credit.infrastructure.adapter.in.rest.dto.OverdueCheckResult dto =
                new com.bank.credit.infrastructure.adapter.in.rest.dto.OverdueCheckResult();
        dto.setAsOf(result.asOf());
        dto.setRanAt(toOffset(result.ranAt()));
        dto.setCreditsMarked(result.creditsMarked());
        dto.setCardsMarked(result.cardsMarked());
        dto.setCustomersAffected(result.customersAffected());
        return dto;
    }

    public com.bank.credit.infrastructure.adapter.in.rest.dto.RecoveryResult toDto(RecoveryResult result) {
        com.bank.credit.infrastructure.adapter.in.rest.dto.RecoveryResult dto =
                new com.bank.credit.infrastructure.adapter.in.rest.dto.RecoveryResult();
        dto.setRanAt(toOffset(result.ranAt()));
        dto.setOlderThanMinutes(result.olderThanMinutes());
        dto.setFound(result.found());
        dto.setRecorded(result.recorded());
        dto.setStillPending(result.stillPending());
        return dto;
    }

    private static OffsetDateTime toOffset(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}

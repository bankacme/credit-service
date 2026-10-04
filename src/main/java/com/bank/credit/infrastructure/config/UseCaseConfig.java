package com.bank.credit.infrastructure.config;

import com.bank.credit.application.port.out.CreditCardRepositoryPort;
import com.bank.credit.application.port.out.CreditEventPublisherPort;
import com.bank.credit.application.port.out.CreditRepositoryPort;
import com.bank.credit.application.port.out.CustomerLookupPort;
import com.bank.credit.application.port.out.MovementRecorderPort;
import com.bank.credit.application.port.out.OperationLogPort;
import com.bank.credit.application.port.out.OverdueQueryPort;
import com.bank.credit.application.port.out.UnitOfWorkPort;
import com.bank.credit.application.usecase.ChangeCreditLimitUseCaseImpl;
import com.bank.credit.application.usecase.ChargeCreditCardUseCaseImpl;
import com.bank.credit.application.usecase.CheckOverdueUseCaseImpl;
import com.bank.credit.application.usecase.CloseCreditCardUseCaseImpl;
import com.bank.credit.application.usecase.CloseCreditUseCaseImpl;
import com.bank.credit.application.usecase.FindCreditCardUseCaseImpl;
import com.bank.credit.application.usecase.FindCreditCardsUseCaseImpl;
import com.bank.credit.application.usecase.FindCreditUseCaseImpl;
import com.bank.credit.application.usecase.FindCreditsUseCaseImpl;
import com.bank.credit.application.usecase.GetCreditCardBalanceUseCaseImpl;
import com.bank.credit.application.usecase.GetPaymentInfoUseCaseImpl;
import com.bank.credit.application.usecase.HistoryRecorder;
import com.bank.credit.application.usecase.IssueCreditCardUseCaseImpl;
import com.bank.credit.application.usecase.OpenCreditUseCaseImpl;
import com.bank.credit.application.usecase.OverdueClearance;
import com.bank.credit.application.usecase.PayCreditCardUseCaseImpl;
import com.bank.credit.application.usecase.PayCreditUseCaseImpl;
import com.bank.credit.application.usecase.RecoverUnrecordedOperationsUseCaseImpl;
import com.bank.credit.application.usecase.RescheduleCreditUseCaseImpl;
import com.bank.credit.domain.service.AcquisitionPolicy;
import com.bank.credit.domain.service.CardNumberGenerator;
import java.security.SecureRandom;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Un @Bean por caso de uso: las clases de application no llevan anotaciones de Spring, igual que en
 * account-service y transaction-service. Las propiedades vienen del Config Server (data-model §8),
 * con los valores propuestos como respaldo.
 */
@Configuration
public class UseCaseConfig {

    private final boolean demoMode;
    private final int paymentTermDays;
    private final int recorderPendingMinutes;

    public UseCaseConfig(@Value("${credit.demo-mode:false}") boolean demoMode,
                         @Value("${credit.card.payment-term-days:30}") int paymentTermDays,
                         @Value("${credit.recorder.pending-minutes:2}") int recorderPendingMinutes) {
        this.demoMode = demoMode;
        this.paymentTermDays = paymentTermDays;
        this.recorderPendingMinutes = recorderPendingMinutes;
    }

    // ---- servicios de dominio y ayudantes compartidos ----

    @Bean
    public AcquisitionPolicy acquisitionPolicy() {
        return new AcquisitionPolicy();
    }

    @Bean
    public CardNumberGenerator cardNumberGenerator() {
        return new CardNumberGenerator();
    }

    @Bean
    public HistoryRecorder historyRecorder(MovementRecorderPort recorderPort, OperationLogPort operationLogPort,
                                           Clock clock) {
        return new HistoryRecorder(recorderPort, operationLogPort, clock);
    }

    @Bean
    public OverdueClearance overdueClearance(OverdueQueryPort overdueQueryPort,
                                             CreditEventPublisherPort eventPublisherPort, Clock clock) {
        return new OverdueClearance(overdueQueryPort, eventPublisherPort, clock);
    }

    // ---- créditos ----

    @Bean
    public OpenCreditUseCaseImpl openCreditUseCase(CustomerLookupPort customerLookupPort,
            CreditRepositoryPort creditRepositoryPort, OverdueQueryPort overdueQueryPort,
            CreditEventPublisherPort eventPublisherPort, AcquisitionPolicy acquisitionPolicy, Clock clock) {
        return new OpenCreditUseCaseImpl(customerLookupPort, creditRepositoryPort, overdueQueryPort,
                eventPublisherPort, acquisitionPolicy, demoMode, clock);
    }

    @Bean
    public FindCreditUseCaseImpl findCreditUseCase(CreditRepositoryPort creditRepositoryPort) {
        return new FindCreditUseCaseImpl(creditRepositoryPort);
    }

    @Bean
    public FindCreditsUseCaseImpl findCreditsUseCase(CreditRepositoryPort creditRepositoryPort) {
        return new FindCreditsUseCaseImpl(creditRepositoryPort);
    }

    @Bean
    public RescheduleCreditUseCaseImpl rescheduleCreditUseCase(CreditRepositoryPort creditRepositoryPort,
            CreditEventPublisherPort eventPublisherPort, OverdueClearance overdueClearance, Clock clock) {
        return new RescheduleCreditUseCaseImpl(creditRepositoryPort, eventPublisherPort, overdueClearance, demoMode,
                clock);
    }

    @Bean
    public CloseCreditUseCaseImpl closeCreditUseCase(CreditRepositoryPort creditRepositoryPort,
            CreditEventPublisherPort eventPublisherPort, Clock clock) {
        return new CloseCreditUseCaseImpl(creditRepositoryPort, eventPublisherPort, clock);
    }

    @Bean
    public PayCreditUseCaseImpl payCreditUseCase(CreditRepositoryPort creditRepositoryPort,
            OperationLogPort operationLogPort, UnitOfWorkPort unitOfWorkPort,
            CreditEventPublisherPort eventPublisherPort, OverdueClearance overdueClearance,
            HistoryRecorder historyRecorder, Clock clock) {
        return new PayCreditUseCaseImpl(creditRepositoryPort, operationLogPort, unitOfWorkPort, eventPublisherPort,
                overdueClearance, historyRecorder, clock);
    }

    // ---- tarjetas ----

    @Bean
    public IssueCreditCardUseCaseImpl issueCreditCardUseCase(CustomerLookupPort customerLookupPort,
            CreditCardRepositoryPort cardRepositoryPort, OverdueQueryPort overdueQueryPort,
            CreditEventPublisherPort eventPublisherPort, AcquisitionPolicy acquisitionPolicy,
            CardNumberGenerator cardNumberGenerator, Clock clock) {
        return new IssueCreditCardUseCaseImpl(customerLookupPort, cardRepositoryPort, overdueQueryPort,
                eventPublisherPort, acquisitionPolicy, cardNumberGenerator, new SecureRandom(), clock);
    }

    @Bean
    public FindCreditCardUseCaseImpl findCreditCardUseCase(CreditCardRepositoryPort cardRepositoryPort) {
        return new FindCreditCardUseCaseImpl(cardRepositoryPort);
    }

    @Bean
    public FindCreditCardsUseCaseImpl findCreditCardsUseCase(CreditCardRepositoryPort cardRepositoryPort) {
        return new FindCreditCardsUseCaseImpl(cardRepositoryPort);
    }

    @Bean
    public ChangeCreditLimitUseCaseImpl changeCreditLimitUseCase(CreditCardRepositoryPort cardRepositoryPort,
            CreditEventPublisherPort eventPublisherPort, Clock clock) {
        return new ChangeCreditLimitUseCaseImpl(cardRepositoryPort, eventPublisherPort, clock);
    }

    @Bean
    public CloseCreditCardUseCaseImpl closeCreditCardUseCase(CreditCardRepositoryPort cardRepositoryPort,
            CreditEventPublisherPort eventPublisherPort, Clock clock) {
        return new CloseCreditCardUseCaseImpl(cardRepositoryPort, eventPublisherPort, clock);
    }

    @Bean
    public ChargeCreditCardUseCaseImpl chargeCreditCardUseCase(CreditCardRepositoryPort cardRepositoryPort,
            OperationLogPort operationLogPort, UnitOfWorkPort unitOfWorkPort,
            CreditEventPublisherPort eventPublisherPort, HistoryRecorder historyRecorder, Clock clock) {
        return new ChargeCreditCardUseCaseImpl(cardRepositoryPort, operationLogPort, unitOfWorkPort,
                eventPublisherPort, historyRecorder, paymentTermDays, clock);
    }

    @Bean
    public PayCreditCardUseCaseImpl payCreditCardUseCase(CreditCardRepositoryPort cardRepositoryPort,
            OperationLogPort operationLogPort, UnitOfWorkPort unitOfWorkPort,
            CreditEventPublisherPort eventPublisherPort, OverdueClearance overdueClearance,
            HistoryRecorder historyRecorder, Clock clock) {
        return new PayCreditCardUseCaseImpl(cardRepositoryPort, operationLogPort, unitOfWorkPort, eventPublisherPort,
                overdueClearance, historyRecorder, clock);
    }

    @Bean
    public GetCreditCardBalanceUseCaseImpl getCreditCardBalanceUseCase(CreditCardRepositoryPort cardRepositoryPort,
                                                                       Clock clock) {
        return new GetCreditCardBalanceUseCaseImpl(cardRepositoryPort, clock);
    }

    @Bean
    public GetPaymentInfoUseCaseImpl getPaymentInfoUseCase(CreditRepositoryPort creditRepositoryPort,
                                                           CreditCardRepositoryPort cardRepositoryPort) {
        return new GetPaymentInfoUseCaseImpl(creditRepositoryPort, cardRepositoryPort);
    }

    // ---- procesos ----

    @Bean
    public CheckOverdueUseCaseImpl checkOverdueUseCase(CreditRepositoryPort creditRepositoryPort,
            CreditCardRepositoryPort cardRepositoryPort, CreditEventPublisherPort eventPublisherPort, Clock clock) {
        return new CheckOverdueUseCaseImpl(creditRepositoryPort, cardRepositoryPort, eventPublisherPort, clock);
    }

    @Bean
    public RecoverUnrecordedOperationsUseCaseImpl recoverUnrecordedOperationsUseCase(
            OperationLogPort operationLogPort, HistoryRecorder historyRecorder, Clock clock) {
        return new RecoverUnrecordedOperationsUseCaseImpl(operationLogPort, historyRecorder, recorderPendingMinutes,
                clock);
    }
}

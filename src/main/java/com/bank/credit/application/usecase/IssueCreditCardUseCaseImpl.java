package com.bank.credit.application.usecase;

import com.bank.credit.application.command.IssueCreditCardCommand;
import com.bank.credit.application.port.in.IssueCreditCardUseCase;
import com.bank.credit.application.port.out.CreditCardRepositoryPort;
import com.bank.credit.application.port.out.CreditEventPublisherPort;
import com.bank.credit.application.port.out.CustomerLookupPort;
import com.bank.credit.application.port.out.OverdueQueryPort;
import com.bank.credit.domain.event.CreditCardIssued;
import com.bank.credit.domain.exception.CardNumberCollisionException;
import com.bank.credit.domain.model.CreditCard;
import com.bank.credit.domain.model.OwnerType;
import com.bank.credit.domain.model.ProductType;
import com.bank.credit.domain.service.AcquisitionPolicy;
import com.bank.credit.domain.service.CardNumberGenerator;
import com.bank.credit.domain.service.validation.AcquisitionContext;
import io.reactivex.rxjava3.core.Single;
import java.time.Clock;
import java.util.random.RandomGenerator;

/**
 * Emite una tarjeta aplicando AcquisitionPolicy (sin tope de tarjetas por cliente). Si el número
 * generado choca con el índice único, genera otro: máximo 3 intentos (data-model 3.2).
 */
public class IssueCreditCardUseCaseImpl implements IssueCreditCardUseCase {

    private static final int MAX_NUMBER_ATTEMPTS = 3;

    private final CustomerLookupPort customerLookupPort;
    private final CreditCardRepositoryPort cardRepositoryPort;
    private final OverdueQueryPort overdueQueryPort;
    private final CreditEventPublisherPort eventPublisherPort;
    private final AcquisitionPolicy acquisitionPolicy;
    private final CardNumberGenerator cardNumberGenerator;
    private final RandomGenerator random;
    private final Clock clock;

    public IssueCreditCardUseCaseImpl(CustomerLookupPort customerLookupPort,
                                      CreditCardRepositoryPort cardRepositoryPort, OverdueQueryPort overdueQueryPort,
                                      CreditEventPublisherPort eventPublisherPort,
                                      AcquisitionPolicy acquisitionPolicy, CardNumberGenerator cardNumberGenerator,
                                      RandomGenerator random, Clock clock) {
        this.customerLookupPort = customerLookupPort;
        this.cardRepositoryPort = cardRepositoryPort;
        this.overdueQueryPort = overdueQueryPort;
        this.eventPublisherPort = eventPublisherPort;
        this.acquisitionPolicy = acquisitionPolicy;
        this.cardNumberGenerator = cardNumberGenerator;
        this.random = random;
        this.clock = clock;
    }

    @Override
    public Single<CreditCard> execute(IssueCreditCardCommand command) {
        String customerId = command.customerId();
        return customerLookupPort.findById(customerId)
                .map(java.util.Optional::of)
                .defaultIfEmpty(java.util.Optional.empty())
                .flatMap(customer -> overdueQueryPort.existsOverdueByCustomer(customerId)
                        .map(hasOverdue -> acquisitionPolicy.evaluate(new AcquisitionContext(
                                ProductType.CREDIT_CARD, customerId, customer.orElse(null), false, hasOverdue))))
                .flatMap(ownerType -> saveWithFreshNumber(command, ownerType))
                .flatMap(saved -> eventPublisherPort.publish(CreditCardIssued.from(saved))
                        .andThen(Single.just(saved)));
    }

    private Single<CreditCard> saveWithFreshNumber(IssueCreditCardCommand command, OwnerType ownerType) {
        return Single.defer(() -> cardRepositoryPort.save(CreditCard.issue(command.customerId(), ownerType,
                        cardNumberGenerator.generate(random), command.creditLimit(), clock)))
                .retry(MAX_NUMBER_ATTEMPTS - 1, CardNumberCollisionException.class::isInstance);
    }
}

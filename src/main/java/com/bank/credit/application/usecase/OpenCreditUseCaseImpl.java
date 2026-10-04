package com.bank.credit.application.usecase;

import com.bank.credit.application.command.OpenCreditCommand;
import com.bank.credit.application.port.in.OpenCreditUseCase;
import com.bank.credit.application.port.out.CreditEventPublisherPort;
import com.bank.credit.application.port.out.CreditRepositoryPort;
import com.bank.credit.application.port.out.CustomerLookupPort;
import com.bank.credit.application.port.out.OverdueQueryPort;
import com.bank.credit.domain.event.CreditOpened;
import com.bank.credit.domain.model.Credit;
import com.bank.credit.domain.model.CustomerSnapshot;
import com.bank.credit.domain.model.OwnerType;
import com.bank.credit.domain.model.ProductType;
import com.bank.credit.domain.service.AcquisitionPolicy;
import com.bank.credit.domain.service.validation.AcquisitionContext;
import io.reactivex.rxjava3.core.Single;
import java.time.Clock;
import java.util.Optional;

/** Reúne los datos por puertos, aplica AcquisitionPolicy, abre el crédito, lo guarda y publica. */
public class OpenCreditUseCaseImpl implements OpenCreditUseCase {

    private final CustomerLookupPort customerLookupPort;
    private final CreditRepositoryPort creditRepositoryPort;
    private final OverdueQueryPort overdueQueryPort;
    private final CreditEventPublisherPort eventPublisherPort;
    private final AcquisitionPolicy acquisitionPolicy;
    private final boolean demoMode;
    private final Clock clock;

    public OpenCreditUseCaseImpl(CustomerLookupPort customerLookupPort, CreditRepositoryPort creditRepositoryPort,
                                 OverdueQueryPort overdueQueryPort, CreditEventPublisherPort eventPublisherPort,
                                 AcquisitionPolicy acquisitionPolicy, boolean demoMode, Clock clock) {
        this.customerLookupPort = customerLookupPort;
        this.creditRepositoryPort = creditRepositoryPort;
        this.overdueQueryPort = overdueQueryPort;
        this.eventPublisherPort = eventPublisherPort;
        this.acquisitionPolicy = acquisitionPolicy;
        this.demoMode = demoMode;
        this.clock = clock;
    }

    @Override
    public Single<Credit> execute(OpenCreditCommand command) {
        String customerId = command.customerId();
        return customerLookupPort.findById(customerId)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(customer -> creditRepositoryPort.existsUnpaidByCustomer(customerId)
                        .flatMap(hasUnpaid -> overdueQueryPort.existsOverdueByCustomer(customerId)
                                .map(hasOverdue -> context(customerId, customer, hasUnpaid, hasOverdue))))
                .map(context -> {
                    OwnerType ownerType = acquisitionPolicy.evaluate(context);
                    return Credit.open(customerId, ownerType, command.amount(), command.dueDate(), demoMode, clock);
                })
                .flatMap(creditRepositoryPort::save)
                .flatMap(saved -> eventPublisherPort.publish(CreditOpened.from(saved)).andThen(Single.just(saved)));
    }

    private static AcquisitionContext context(String customerId, Optional<CustomerSnapshot> customer,
                                              boolean hasUnpaid, boolean hasOverdue) {
        return new AcquisitionContext(ProductType.CREDIT, customerId, customer.orElse(null), hasUnpaid, hasOverdue);
    }
}

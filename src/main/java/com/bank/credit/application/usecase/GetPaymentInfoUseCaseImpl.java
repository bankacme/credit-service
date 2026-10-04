package com.bank.credit.application.usecase;

import com.bank.credit.application.port.in.GetPaymentInfoUseCase;
import com.bank.credit.application.port.out.CreditCardRepositoryPort;
import com.bank.credit.application.port.out.CreditRepositoryPort;
import com.bank.credit.application.view.PaymentInfoView;
import com.bank.credit.domain.exception.CreditCardNotFoundException;
import com.bank.credit.domain.exception.CreditNotFoundException;
import com.bank.credit.domain.model.CreditCardId;
import com.bank.credit.domain.model.CreditId;
import com.bank.credit.domain.model.ProductType;
import io.reactivex.rxjava3.core.Single;

/** GET /credits/{id}/payment-info y GET /credit-cards/{id}/payment-info (vista limitada para terceros). */
public class GetPaymentInfoUseCaseImpl implements GetPaymentInfoUseCase {

    private final CreditRepositoryPort creditRepositoryPort;
    private final CreditCardRepositoryPort cardRepositoryPort;

    public GetPaymentInfoUseCaseImpl(CreditRepositoryPort creditRepositoryPort,
                                     CreditCardRepositoryPort cardRepositoryPort) {
        this.creditRepositoryPort = creditRepositoryPort;
        this.cardRepositoryPort = cardRepositoryPort;
    }

    @Override
    public Single<PaymentInfoView> execute(ProductType productType, String productId) {
        return switch (productType) {
            case CREDIT -> creditRepositoryPort.findById(new CreditId(productId))
                    .switchIfEmpty(Single.error(() -> new CreditNotFoundException(productId)))
                    .map(PaymentInfoView::of);
            case CREDIT_CARD -> cardRepositoryPort.findById(new CreditCardId(productId))
                    .switchIfEmpty(Single.error(() -> new CreditCardNotFoundException(productId)))
                    .map(PaymentInfoView::of);
        };
    }
}

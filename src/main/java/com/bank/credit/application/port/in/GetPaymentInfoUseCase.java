package com.bank.credit.application.port.in;

import com.bank.credit.application.view.PaymentInfoView;
import com.bank.credit.domain.model.ProductType;
import io.reactivex.rxjava3.core.Single;

public interface GetPaymentInfoUseCase {

    Single<PaymentInfoView> execute(ProductType productType, String productId);
}

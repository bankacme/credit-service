package com.bank.credit.application.port.in;

import com.bank.credit.application.view.RecoveryResult;
import io.reactivex.rxjava3.core.Single;

public interface RecoverUnrecordedOperationsUseCase {

    Single<RecoveryResult> execute(Integer olderThanMinutesOverride);
}

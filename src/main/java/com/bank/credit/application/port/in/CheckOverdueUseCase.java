package com.bank.credit.application.port.in;

import com.bank.credit.application.view.OverdueCheckResult;
import io.reactivex.rxjava3.core.Single;
import java.time.LocalDate;

public interface CheckOverdueUseCase {

    Single<OverdueCheckResult> execute(LocalDate asOfOverride);
}

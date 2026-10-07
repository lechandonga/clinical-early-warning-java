package com.github.lechandonga.clinicalearlywarningjava.validation;

import com.github.lechandonga.clinicalearlywarningjava.domain.RejectionReason;

/**
 * 数据校验失败异常，携带可区分的拒绝原因。
 */
public class ObservationRejectedException extends RuntimeException {

    private final RejectionReason reason;

    public ObservationRejectedException(RejectionReason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public RejectionReason getReason() {
        return reason;
    }
}

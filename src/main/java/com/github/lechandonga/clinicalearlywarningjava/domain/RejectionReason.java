package com.github.lechandonga.clinicalearlywarningjava.domain;

/**
 * 数据上报被拒绝的可区分原因。
 */
public enum RejectionReason {
    MISSING_FIELD,
    UNKNOWN_CODE,
    VALUE_OUT_OF_RANGE,
    TIMESTAMP_IN_FUTURE,
    TIMESTAMP_TOO_OLD
}

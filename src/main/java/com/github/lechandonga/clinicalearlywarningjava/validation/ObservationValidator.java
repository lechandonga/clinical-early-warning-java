package com.github.lechandonga.clinicalearlywarningjava.validation;

import com.github.lechandonga.clinicalearlywarningjava.config.EarlyWarningProperties;
import com.github.lechandonga.clinicalearlywarningjava.domain.Observation;
import com.github.lechandonga.clinicalearlywarningjava.domain.RejectionReason;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * 上报数据校验：关键字段缺失、取值超出配置范围、时间戳异常一律拒绝，
 * 并给出可区分的原因，绝不静默接受。
 */
@Component
public class ObservationValidator {

    private final EarlyWarningProperties properties;
    private final Clock clock;

    @Autowired
    public ObservationValidator(EarlyWarningProperties properties) {
        this(properties, Clock.systemUTC());
    }

    public ObservationValidator(EarlyWarningProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public void validate(Observation o) {
        if (isBlank(o.recordId()) || isBlank(o.patientId()) || isBlank(o.code())
                || o.type() == null || o.value() == null || o.occurredAt() == null) {
            throw new ObservationRejectedException(RejectionReason.MISSING_FIELD,
                    "关键字段缺失: recordId/patientId/type/code/value/occurredAt 均不能为空");
        }
        if (o.value().isNaN() || o.value().isInfinite()) {
            throw new ObservationRejectedException(RejectionReason.VALUE_OUT_OF_RANGE,
                    "数值非法: " + o.value());
        }
        EarlyWarningProperties.Range range = properties.getRanges().get(o.code());
        if (range == null) {
            throw new ObservationRejectedException(RejectionReason.UNKNOWN_CODE,
                    "未配置的观测项目编码: " + o.code());
        }
        if (o.value() < range.min() || o.value() > range.max()) {
            throw new ObservationRejectedException(RejectionReason.VALUE_OUT_OF_RANGE,
                    "%s=%.1f 超出配置范围 [%.1f, %.1f]".formatted(o.code(), o.value(), range.min(), range.max()));
        }
        Instant now = clock.instant();
        if (o.occurredAt().isAfter(now.plus(properties.getMaxFutureSkew()))) {
            throw new ObservationRejectedException(RejectionReason.TIMESTAMP_IN_FUTURE,
                    "时间戳超前当前时间超过 " + properties.getMaxFutureSkew() + ": " + o.occurredAt());
        }
        if (o.occurredAt().isBefore(now.minus(properties.getMaxPastAge()))) {
            throw new ObservationRejectedException(RejectionReason.TIMESTAMP_TOO_OLD,
                    "时间戳早于最大允许历史 " + properties.getMaxPastAge() + ": " + o.occurredAt());
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}

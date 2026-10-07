package com.github.lechandonga.clinicalearlywarningjava.domain;

import java.time.Instant;

/**
 * 触发依据：某指标命中某条件时的取值、阈值与分值。
 */
public record TriggerEvidence(
        String metricCode,
        String operator,
        double threshold,
        double actualValue,
        Instant observedAt,
        int score
) {
}

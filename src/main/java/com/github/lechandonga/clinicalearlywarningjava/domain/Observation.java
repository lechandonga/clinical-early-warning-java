package com.github.lechandonga.clinicalearlywarningjava.domain;

import java.time.Instant;

/**
 * 一条体征 / 检验观测。观测一经接受即不可变，按 (patientId, 指标, 观测时间点) 归并。
 */
public record Observation(
        String recordId,
        String patientId,
        String metricCode,
        double value,
        Instant observedAt,
        Instant receivedAt
) {
}

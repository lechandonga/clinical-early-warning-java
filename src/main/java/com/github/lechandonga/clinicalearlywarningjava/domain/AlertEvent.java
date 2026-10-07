package com.github.lechandonga.clinicalearlywarningjava.domain;

import java.time.Instant;

/**
 * 告警流转事件（产生 / 确认 / 升级 / 解除 / 确认后再次提醒），构成可审计的流转轨迹。
 */
public record AlertEvent(
        AlertStatus fromStatus,
        AlertStatus toStatus,
        String action,
        String reason,
        String operator,
        Instant occurredAt
) {
}

package com.github.lechandonga.clinicalearlywarningjava.domain;

/**
 * 告警生命周期状态：产生 -> 确认 -> 升级 -> 解除。
 */
public enum AlertStatus {
    ACTIVE,
    ACKNOWLEDGED,
    ESCALATED,
    RESOLVED
}

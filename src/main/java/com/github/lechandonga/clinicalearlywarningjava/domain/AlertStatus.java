package com.github.lechandonga.clinicalearlywarningjava.domain;

/**
 * 告警状态机：
 * OPEN -> ACKNOWLEDGED -> ESCALATED -> RESOLVED
 * OPEN -> ESCALATED, OPEN -> RESOLVED, ACKNOWLEDGED -> RESOLVED 亦合法。
 * RESOLVED 为终态。
 */
public enum AlertStatus {
    OPEN,
    ACKNOWLEDGED,
    ESCALATED,
    RESOLVED
}

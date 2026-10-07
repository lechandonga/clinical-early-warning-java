package com.github.lechandonga.clinicalearlywarningjava.domain;

import java.time.Instant;

/**
 * 告警生命周期事件，提供完整可解释的流转轨迹。
 */
public record AlertEvent(
        Type type,
        AlertStatus fromStatus,
        AlertStatus toStatus,
        RiskLevel riskLevel,
        String detail,
        String ruleVersion,
        Instant at) {

    public enum Type {
        CREATED,
        ACKNOWLEDGED,
        ESCALATED,
        RESOLVED,
        /** 已确认后风险继续升高，按规则再次提醒 */
        RE_NOTIFIED
    }
}

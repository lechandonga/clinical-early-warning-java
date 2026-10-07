package com.github.lechandonga.clinicalearlywarningjava.alert;

import com.github.lechandonga.clinicalearlywarningjava.domain.AlertStatus;

/**
 * 非法告警状态流转异常，说明当前状态与目标状态。
 */
public class AlertTransitionException extends RuntimeException {

    private final AlertStatus current;
    private final AlertStatus target;

    public AlertTransitionException(String alertId, AlertStatus current, AlertStatus target) {
        super("告警 %s 不允许从 %s 流转到 %s".formatted(alertId, current, target));
        this.current = current;
        this.target = target;
    }

    public AlertStatus getCurrent() {
        return current;
    }

    public AlertStatus getTarget() {
        return target;
    }
}

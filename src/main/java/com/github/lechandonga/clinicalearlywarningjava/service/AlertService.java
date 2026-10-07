package com.github.lechandonga.clinicalearlywarningjava.service;

import com.github.lechandonga.clinicalearlywarningjava.domain.Alert;
import com.github.lechandonga.clinicalearlywarningjava.domain.AlertAction;
import com.github.lechandonga.clinicalearlywarningjava.domain.AlertStatus;
import com.github.lechandonga.clinicalearlywarningjava.exception.BusinessException;
import com.github.lechandonga.clinicalearlywarningjava.store.AlertStore;
import com.github.lechandonga.clinicalearlywarningjava.store.PatientLocks;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 告警闭环：产生(自动) -> 确认 -> 升级 -> 解除。非法流转被拒绝并说明原因。
 */
@Service
public class AlertService {

    private static final Set<AlertStatus> ACK_ALLOWED = EnumSet.of(AlertStatus.ACTIVE, AlertStatus.ESCALATED);
    private static final Set<AlertStatus> ESCALATE_ALLOWED =
            EnumSet.of(AlertStatus.ACTIVE, AlertStatus.ACKNOWLEDGED);
    private static final Set<AlertStatus> RESOLVE_ALLOWED =
            EnumSet.of(AlertStatus.ACTIVE, AlertStatus.ACKNOWLEDGED, AlertStatus.ESCALATED);

    private final AlertStore alertStore;
    private final PatientLocks patientLocks;
    private final Clock clock;

    public AlertService(AlertStore alertStore, PatientLocks patientLocks, Clock clock) {
        this.alertStore = alertStore;
        this.patientLocks = patientLocks;
        this.clock = clock;
    }

    public Alert transition(String alertId, AlertAction action, String operator, String reason) {
        Alert alert = alertStore.findById(alertId)
                .orElseThrow(() -> new BusinessException("ALERT_NOT_FOUND", "告警不存在: " + alertId));
        if (operator == null || operator.isBlank()) {
            throw new BusinessException("OPERATOR_REQUIRED", "状态流转必须指定操作人");
        }
        return patientLocks.withLock(alert.getPatientId(), () -> doTransition(alert, action, operator, reason));
    }

    private Alert doTransition(Alert alert, AlertAction action, String operator, String reason) {
        AlertStatus status = alert.getStatus();
        String why = reason == null || reason.isBlank() ? "(未填写)" : reason;
        switch (action) {
            case ACKNOWLEDGE -> {
                if (!ACK_ALLOWED.contains(status)) {
                    throw new BusinessException("ILLEGAL_TRANSITION",
                            "非法流转: 已" + statusLabel(status) + "的告警不能确认，仅 " + labelSet(ACK_ALLOWED) + " 可确认");
                }
                alert.acknowledge(operator, why, clock.instant());
            }
            case ESCALATE -> {
                if (!ESCALATE_ALLOWED.contains(status)) {
                    throw new BusinessException("ILLEGAL_TRANSITION",
                            "非法流转: 已" + statusLabel(status) + "的告警不能升级，仅 " + labelSet(ESCALATE_ALLOWED) + " 可升级");
                }
                alert.escalate(operator, why, clock.instant());
            }
            case RESOLVE -> {
                if (!RESOLVE_ALLOWED.contains(status)) {
                    throw new BusinessException("ILLEGAL_TRANSITION",
                            "非法流转: 已" + statusLabel(status) + "的告警不能解除，已解除告警不可重复操作");
                }
                alert.resolve(operator, why, clock.instant());
                alertStore.markResolved(alert);
            }
        }
        return alert;
    }

    public Alert get(String alertId) {
        return alertStore.findById(alertId)
                .orElseThrow(() -> new BusinessException("ALERT_NOT_FOUND", "告警不存在: " + alertId));
    }

    public List<Alert> listPatient(String patientId) {
        return alertStore.listByPatient(patientId);
    }

    public List<Alert> listOpen() {
        return alertStore.listOpen();
    }

    private String statusLabel(AlertStatus s) {
        return switch (s) {
            case ACTIVE -> "产生未确认";
            case ACKNOWLEDGED -> "确认";
            case ESCALATED -> "升级";
            case RESOLVED -> "解除";
        };
    }

    private String labelSet(Set<AlertStatus> set) {
        return set.stream().map(this::statusLabel).toList().toString();
    }
}

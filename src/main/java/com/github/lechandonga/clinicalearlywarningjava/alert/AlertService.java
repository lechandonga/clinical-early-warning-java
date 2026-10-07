package com.github.lechandonga.clinicalearlywarningjava.alert;

import com.github.lechandonga.clinicalearlywarningjava.domain.Alert;
import com.github.lechandonga.clinicalearlywarningjava.domain.AlertEvent;
import com.github.lechandonga.clinicalearlywarningjava.domain.AlertStatus;
import com.github.lechandonga.clinicalearlywarningjava.domain.RiskLevel;
import com.github.lechandonga.clinicalearlywarningjava.rule.RiskEvaluation;
import com.github.lechandonga.clinicalearlywarningjava.rule.RuleRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 告警闭环服务。约定：
 * - 同一患者同一时间最多一条未解除告警，重复触发只更新证据，不产生重复告警；
 * - 状态机 OPEN -> ACKNOWLEDGED -> ESCALATED -> RESOLVED（OPEN 也可直接 ESCALATED/RESOLVED）；
 * - RESOLVED 为终态，任何自 RESOLVED 的流转都被拒绝；
 * - 已确认（ACKNOWLEDGED）后风险继续升高时按规则再次提醒（RE_NOTIFIED 事件）；
 * - 风险回落到告警阈值以下时自动解除。
 * 所有方法由调用方在患者级锁内调用，保证并发一致性。
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final RuleRegistry ruleRegistry;
    private final Map<String, Alert> alertsById = new ConcurrentHashMap<>();
    private final Map<String, String> activeAlertByPatient = new ConcurrentHashMap<>();

    public AlertService(RuleRegistry ruleRegistry) {
        this.ruleRegistry = ruleRegistry;
    }

    /**
     * 根据一次风险判定推进告警生命周期，返回告警（无告警需求时返回空）。
     */
    public Optional<Alert> applyEvaluation(RiskEvaluation eval) {
        String patientId = eval.patientId();
        boolean shouldAlert = eval.level().ordinal() >= alertThreshold(eval).ordinal();
        String activeId = activeAlertByPatient.get(patientId);
        Alert active = activeId == null ? null : alertsById.get(activeId);

        if (!shouldAlert) {
            if (active != null) {
                active.transition(AlertStatus.RESOLVED, AlertEvent.Type.RESOLVED,
                        "风险回落至 %s，自动解除（规则版本 %s）".formatted(eval.level(), eval.ruleVersion()),
                        Instant.now());
                activeAlertByPatient.remove(patientId);
                log.info("告警自动解除 alertId={} patient={} level={}", active.getAlertId(), patientId, eval.level());
            }
            return Optional.ofNullable(active);
        }

        if (active == null) {
            Alert alert = new Alert(UUID.randomUUID().toString(), patientId, eval.level(),
                    eval.ruleVersion(), eval.explain(), Instant.now());
            alertsById.put(alert.getAlertId(), alert);
            activeAlertByPatient.put(patientId, alert.getAlertId());
            log.warn("产生告警 alertId={} patient={} level={} ruleVersion={} 依据: {}",
                    alert.getAlertId(), patientId, eval.level(), eval.ruleVersion(), eval.explain());
            return Optional.of(alert);
        }

        if (eval.level().higherThan(active.getRiskLevel())) {
            active.reNotify(eval.level(), eval.explain(), eval.ruleVersion(), Instant.now());
            log.warn("风险继续升高，再次提醒 alertId={} patient={} {} -> {} status={}",
                    active.getAlertId(), patientId, active.getRiskLevel(), eval.level(), active.getStatus());
        }
        return Optional.of(active);
    }

    private RiskLevel alertThreshold(RiskEvaluation eval) {
        // 阈值取自判定所用规则版本，保证历史版本重放结论一致
        return ruleRegistry.find(eval.ruleVersion())
                .map(v -> v.alertFromLevel())
                .orElse(RiskLevel.MEDIUM);
    }

    public Alert acknowledge(String alertId) {
        return transition(alertId, AlertStatus.ACKNOWLEDGED, AlertEvent.Type.ACKNOWLEDGED, "人工确认");
    }

    public Alert escalate(String alertId) {
        return transition(alertId, AlertStatus.ESCALATED, AlertEvent.Type.ESCALATED, "人工升级");
    }

    public Alert resolve(String alertId) {
        Alert alert = transition(alertId, AlertStatus.RESOLVED, AlertEvent.Type.RESOLVED, "人工解除");
        activeAlertByPatient.remove(alert.getPatientId(), alertId);
        return alert;
    }

    private Alert transition(String alertId, AlertStatus target, AlertEvent.Type eventType, String detail) {
        Alert alert = alertsById.get(alertId);
        if (alert == null) {
            throw new AlertNotFoundException(alertId);
        }
        synchronized (alert) {
            AlertStatus current = alert.getStatus();
            if (!isLegal(current, target)) {
                throw new AlertTransitionException(alertId, current, target);
            }
            alert.transition(target, eventType, detail, Instant.now());
            log.info("告警流转 alertId={} {} -> {} ({})", alertId, current, target, detail);
            return alert;
        }
    }

    private boolean isLegal(AlertStatus from, AlertStatus to) {
        return switch (from) {
            case OPEN -> to == AlertStatus.ACKNOWLEDGED || to == AlertStatus.ESCALATED || to == AlertStatus.RESOLVED;
            case ACKNOWLEDGED -> to == AlertStatus.ESCALATED || to == AlertStatus.RESOLVED;
            case ESCALATED -> to == AlertStatus.RESOLVED;
            case RESOLVED -> false;
        };
    }

    public Optional<Alert> find(String alertId) {
        return Optional.ofNullable(alertsById.get(alertId));
    }

    public List<Alert> all() {
        return List.copyOf(alertsById.values());
    }

    public List<Alert> byPatient(String patientId) {
        return alertsById.values().stream()
                .filter(a -> a.getPatientId().equals(patientId))
                .toList();
    }
}

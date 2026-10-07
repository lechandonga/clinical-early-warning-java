package com.github.lechandonga.clinicalearlywarningjava.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 患者风险告警。同一患者同一时间最多存在一条未解除（非 RESOLVED）告警，
 * 重复触发只更新该告警并追加事件，不产生重复告警。
 */
public class Alert {

    private final String alertId;
    private final String patientId;
    private final Instant createdAt;
    private AlertStatus status;
    private RiskLevel riskLevel;
    private String ruleVersion;
    private String triggerEvidence;
    private final List<AlertEvent> events = new ArrayList<>();

    public Alert(String alertId, String patientId, RiskLevel riskLevel,
                 String ruleVersion, String triggerEvidence, Instant createdAt) {
        this.alertId = alertId;
        this.patientId = patientId;
        this.riskLevel = riskLevel;
        this.ruleVersion = ruleVersion;
        this.triggerEvidence = triggerEvidence;
        this.createdAt = createdAt;
        this.status = AlertStatus.OPEN;
        addEvent(AlertEvent.Type.CREATED, null, AlertStatus.OPEN, "告警产生");
    }

    public synchronized void transition(AlertStatus target, AlertEvent.Type eventType,
                                        String detail, Instant at) {
        AlertStatus from = this.status;
        this.status = target;
        events.add(new AlertEvent(eventType, from, target, riskLevel, detail, ruleVersion, at));
    }

    public synchronized void reNotify(RiskLevel newLevel, String evidence, String atRuleVersion, Instant at) {
        this.riskLevel = newLevel;
        this.triggerEvidence = evidence;
        if (atRuleVersion != null) {
            this.ruleVersion = atRuleVersion;
        }
        events.add(new AlertEvent(AlertEvent.Type.RE_NOTIFIED, status, status,
                newLevel, "风险继续升高，再次提醒: " + evidence, atRuleVersion, at));
    }

    private void addEvent(AlertEvent.Type type, AlertStatus from, AlertStatus to, String detail) {
        events.add(new AlertEvent(type, from, to, riskLevel, detail, ruleVersion, createdAt));
    }

    public String getAlertId() {
        return alertId;
    }

    public String getPatientId() {
        return patientId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public synchronized AlertStatus getStatus() {
        return status;
    }

    public synchronized RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public synchronized String getRuleVersion() {
        return ruleVersion;
    }

    public synchronized String getTriggerEvidence() {
        return triggerEvidence;
    }

    public synchronized List<AlertEvent> getEvents() {
        return Collections.unmodifiableList(new ArrayList<>(events));
    }
}

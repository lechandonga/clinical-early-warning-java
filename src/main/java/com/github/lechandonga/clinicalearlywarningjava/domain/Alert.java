package com.github.lechandonga.clinicalearlywarningjava.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 患者告警。同一患者同一时间只存在一条“未关闭”告警；风险继续升高时在原告警上升级 / 再提醒，
 * 风险回落后人工解除。所有变更在患者级锁内完成。
 */
public class Alert {
    private final String id;
    private final String patientId;
    private final String ruleVersionId;
    private final int initialScore;
    private final AlertSeverity initialSeverity;
    private final Instant createdAt;
    private final List<TriggerEvidence> initialEvidence;

    private AlertStatus status;
    private AlertSeverity severity;
    private int lastScore;
    private int escalationLevel;
    private final List<AlertEvent> events = new ArrayList<>();

    public Alert(String id, String patientId, String ruleVersionId, int initialScore,
                 AlertSeverity initialSeverity, List<TriggerEvidence> initialEvidence,
                 Instant createdAt, Instant triggeredAt) {
        this.id = id;
        this.patientId = patientId;
        this.ruleVersionId = ruleVersionId;
        this.initialScore = initialScore;
        this.initialSeverity = initialSeverity;
        this.createdAt = createdAt;
        this.initialEvidence = List.copyOf(initialEvidence);
        this.status = AlertStatus.ACTIVE;
        this.severity = initialSeverity;
        this.lastScore = initialScore;
        this.events.add(new AlertEvent(null, AlertStatus.ACTIVE, "RAISE",
                "risk score " + initialScore + " => " + initialSeverity, "SYSTEM", triggeredAt));
    }

    public String getId() { return id; }
    public String getPatientId() { return patientId; }
    public String getRuleVersionId() { return ruleVersionId; }
    public int getInitialScore() { return initialScore; }
    public AlertSeverity getInitialSeverity() { return initialSeverity; }
    public Instant getCreatedAt() { return createdAt; }
    public List<TriggerEvidence> getInitialEvidence() { return initialEvidence; }
    public AlertStatus getStatus() { return status; }
    public AlertSeverity getSeverity() { return severity; }
    public int getLastScore() { return lastScore; }
    public int getEscalationLevel() { return escalationLevel; }
    public List<AlertEvent> getEvents() { return Collections.unmodifiableList(events); }

    public boolean isOpen() {
        return status != AlertStatus.RESOLVED;
    }

    public void recordEvent(AlertStatus from, AlertStatus to, String action, String reason,
                            String operator, Instant at) {
        events.add(new AlertEvent(from, to, action, reason, operator, at));
    }

    /** 风险升高：更新分值 / 严重度，未确认则置 ESCALATED，已确认则回到 ESCALATED 并再提醒。 */
    public void applyRiskIncrease(int newScore, AlertSeverity newSeverity, String reason, Instant at) {
        AlertStatus from = this.status;
        this.lastScore = newScore;
        this.severity = newSeverity;
        this.escalationLevel++;
        this.status = AlertStatus.ESCALATED;
        events.add(new AlertEvent(from, AlertStatus.ESCALATED,
                from == AlertStatus.ACKNOWLEDGED ? "RE_ALERT_ESCALATE" : "AUTO_ESCALATE",
                reason, "SYSTEM", at));
    }

    public void acknowledge(String operator, String reason, Instant at) {
        AlertStatus from = this.status;
        this.status = AlertStatus.ACKNOWLEDGED;
        events.add(new AlertEvent(from, AlertStatus.ACKNOWLEDGED, "ACKNOWLEDGE", reason, operator, at));
    }

    public void escalate(String operator, String reason, Instant at) {
        AlertStatus from = this.status;
        this.status = AlertStatus.ESCALATED;
        this.escalationLevel++;
        events.add(new AlertEvent(from, AlertStatus.ESCALATED, "ESCALATE", reason, operator, at));
    }

    public void resolve(String operator, String reason, Instant at) {
        AlertStatus from = this.status;
        this.status = AlertStatus.RESOLVED;
        events.add(new AlertEvent(from, AlertStatus.RESOLVED, "RESOLVE", reason, operator, at));
    }
}

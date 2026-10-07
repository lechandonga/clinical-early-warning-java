package com.github.lechandonga.clinicalearlywarningjava.service;

import com.github.lechandonga.clinicalearlywarningjava.config.ClinicalWarningProperties;
import com.github.lechandonga.clinicalearlywarningjava.domain.Assessment;
import com.github.lechandonga.clinicalearlywarningjava.domain.Alert;
import com.github.lechandonga.clinicalearlywarningjava.domain.AlertSeverity;
import com.github.lechandonga.clinicalearlywarningjava.domain.MetricDef;
import com.github.lechandonga.clinicalearlywarningjava.domain.MetricRegistry;
import com.github.lechandonga.clinicalearlywarningjava.domain.Observation;
import com.github.lechandonga.clinicalearlywarningjava.domain.RuleVersion;
import com.github.lechandonga.clinicalearlywarningjava.exception.BusinessException;
import com.github.lechandonga.clinicalearlywarningjava.exception.ValidationException;
import com.github.lechandonga.clinicalearlywarningjava.store.AlertStore;
import com.github.lechandonga.clinicalearlywarningjava.store.ObservationStore;
import com.github.lechandonga.clinicalearlywarningjava.store.PatientLocks;
import com.github.lechandonga.clinicalearlywarningjava.store.RuleStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 观测上报主流程：校验 -> 存储（支持乱序 / 迟到 / 幂等）-> 按观测时刻选择规则版本重评 -> 驱动告警闭环。
 */
@Service
public class ObservationService {

    private static final Logger log = LoggerFactory.getLogger(ObservationService.class);

    private final ObservationStore observationStore;
    private final RuleStore ruleStore;
    private final AlertStore alertStore;
    private final MetricRegistry metricRegistry;
    private final RuleEngine ruleEngine;
    private final PatientLocks patientLocks;
    private final ClinicalWarningProperties properties;
    private final Clock clock;

    public ObservationService(ObservationStore observationStore, RuleStore ruleStore, AlertStore alertStore,
                              MetricRegistry metricRegistry, RuleEngine ruleEngine, PatientLocks patientLocks,
                              ClinicalWarningProperties properties, Clock clock) {
        this.observationStore = observationStore;
        this.ruleStore = ruleStore;
        this.alertStore = alertStore;
        this.metricRegistry = metricRegistry;
        this.ruleEngine = ruleEngine;
        this.patientLocks = patientLocks;
        this.properties = properties;
        this.clock = clock;
    }

    public record IngestResult(boolean accepted, boolean duplicated, Assessment assessment,
                               Alert alert, String alertAction) {
    }

    public IngestResult ingest(String recordId, String patientId, String metricCode,
                               Double value, Instant observedAt) {
        validateRequired(recordId, "recordId");
        validateRequired(patientId, "patientId");
        validateRequired(metricCode, "metricCode");
        if (value == null) {
            throw ValidationException.missingField("value");
        }
        if (observedAt == null) {
            throw ValidationException.missingField("observedAt");
        }

        MetricDef metric = metricRegistry.find(metricCode)
                .orElseThrow(() -> ValidationException.unknownMetric(metricCode));
        if (value < metric.min() || value > metric.max()) {
            throw ValidationException.outOfRange(metricCode, value, metric.min(), metric.max());
        }

        Instant now = clock.instant();
        if (observedAt.isAfter(now.plus(properties.getMaxTimestampFuture()))) {
            throw ValidationException.timestampInvalid(
                    "观测时间 " + observedAt + " 领先当前时间超过 " + properties.getMaxTimestampFuture());
        }
        if (observedAt.isBefore(now.minus(properties.getMaxTimestampAge()))) {
            throw ValidationException.timestampInvalid(
                    "观测时间 " + observedAt + " 早于当前时间超过 " + properties.getMaxTimestampAge());
        }

        Observation obs = new Observation(recordId, patientId, metricCode, value, observedAt, now);
        return patientLocks.withLock(patientId, () -> doIngest(obs));
    }

    private IngestResult doIngest(Observation obs) {
        if (observationStore.isDuplicateRecord(obs.recordId())) {
            throw ValidationException.duplicateRecord(obs.recordId());
        }
        boolean newlyAdded = observationStore.save(obs);
        if (!newlyAdded) {
            log.info("患者 {} 的指标 {} 在 {} 的数据已存在，按幂等重复处理",
                    obs.patientId(), obs.metricCode(), obs.observedAt());
        }

        RuleVersion rules = ruleStore.effectiveAt(obs.observedAt())
                .orElseThrow(() -> new BusinessException("NO_RULE_VERSION", "当前没有可用的风险规则版本"));

        Instant windowStart = obs.observedAt().minus(properties.getLookback());
        List<Observation> window = observationStore.snapshotAt(obs.patientId(), obs.observedAt()).stream()
                .filter(o -> !o.observedAt().isBefore(windowStart))
                .toList();

        Assessment assessment = ruleEngine.evaluate(obs.patientId(), obs.observedAt(), rules, window);
        alertStore.appendAssessment(assessment);

        AlertActionEffect effect = reconcileAlert(assessment);

        boolean late = obs.observedAt().isBefore(obs.receivedAt().minusSeconds(1));
        log.info("患者 {} 接收观测 recordId={} 指标={} 值={} 观测时刻={} {} 使用规则={} 评分={} 严重度={} 告警动作={}",
                obs.patientId(), obs.recordId(), obs.metricCode(), obs.value(), obs.observedAt(),
                late ? "(迟到)" : "", rules.versionId(), assessment.totalScore(),
                assessment.severity(), effect.action());

        return new IngestResult(true, !newlyAdded, assessment, effect.alert(), effect.action());
    }

    /**
     * 依据最新评估收敛告警状态（在患者级锁内执行）：
     * - 无风险：不自动解除，仅记录（解除必须人工闭环）；
     * - 有风险且无未关闭告警：产生告警；
     * - 风险等级升高或分数继续上升：升级；已确认患者会再提醒（RE_ALERT_ESCALATE）；
     * - 风险未继续升高：不重复产生告警。
     */
    private AlertActionEffect reconcileAlert(Assessment assessment) {
        AlertSeverity severity = assessment.severity();
        if (severity == null) {
            return new AlertActionEffect("NO_ALERT", null);
        }

        Alert open = alertStore.findOpen(assessment.patientId()).orElse(null);
        if (open == null) {
            Alert alert = new Alert(
                    "AL-" + UUID.randomUUID(),
                    assessment.patientId(),
                    assessment.ruleVersionId(),
                    assessment.totalScore(),
                    severity,
                    assessment.evidences(),
                    clock.instant(),
                    assessment.evaluatedAt());
            alertStore.create(alert);
            return new AlertActionEffect("RAISED", alert);
        }

        boolean higherSeverity = severity.ordinal() > open.getSeverity().ordinal();
        boolean higherScore = assessment.totalScore() > open.getLastScore()
                && severity.ordinal() >= open.getSeverity().ordinal();
        if (higherSeverity || higherScore) {
            String reason = "风险继续升高: 评分 " + open.getLastScore() + "->" + assessment.totalScore()
                    + ", 严重度 " + open.getSeverity() + "->" + severity;
            open.applyRiskIncrease(assessment.totalScore(), severity, reason, assessment.evaluatedAt());
            return new AlertActionEffect(
                    higherSeverity ? "ESCALATED" : "REMINDED", open);
        }
        return new AlertActionEffect("UNCHANGED", open);
    }

    private void validateRequired(String value, String field) {
        if (value == null || value.isBlank()) {
            throw ValidationException.missingField(field);
        }
    }

    private record AlertActionEffect(String action, Alert alert) {
    }
}

package com.github.lechandonga.clinicalearlywarningjava.ingest;

import com.github.lechandonga.clinicalearlywarningjava.alert.AlertService;
import com.github.lechandonga.clinicalearlywarningjava.domain.Alert;
import com.github.lechandonga.clinicalearlywarningjava.domain.Observation;
import com.github.lechandonga.clinicalearlywarningjava.rule.RiskEngine;
import com.github.lechandonga.clinicalearlywarningjava.rule.RiskEvaluation;
import com.github.lechandonga.clinicalearlywarningjava.rule.RuleRegistry;
import com.github.lechandonga.clinicalearlywarningjava.rule.RuleVersion;
import com.github.lechandonga.clinicalearlywarningjava.validation.ObservationRejectedException;
import com.github.lechandonga.clinicalearlywarningjava.validation.ObservationValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 观测数据摄入服务。保证：
 * - 校验失败一律拒绝并给出可区分原因，绝不静默接受；
 * - 同一 recordId 重复上报幂等去重；
 * - 乱序 / 迟到数据照常入库并重新触发判定，不会被丢弃；
 * - 患者级锁保证并发上报下判定与告警状态一致；
 * - 每次判定都记录所用规则版本，历史结论不可被规则更新改写。
 */
@Service
public class ObservationService {

    private static final Logger log = LoggerFactory.getLogger(ObservationService.class);

    private final ObservationValidator validator;
    private final RiskEngine riskEngine;
    private final RuleRegistry ruleRegistry;
    private final AlertService alertService;

    private final Map<String, List<Observation>> observationsByPatient = new ConcurrentHashMap<>();
    private final Set<String> seenRecordIds = ConcurrentHashMap.newKeySet();
    private final Map<String, ReentrantLock> patientLocks = new ConcurrentHashMap<>();
    private final List<RiskEvaluation> evaluationLog = new CopyOnWriteArrayList<>();

    public ObservationService(ObservationValidator validator, RiskEngine riskEngine,
                              RuleRegistry ruleRegistry, AlertService alertService) {
        this.validator = validator;
        this.riskEngine = riskEngine;
        this.ruleRegistry = ruleRegistry;
        this.alertService = alertService;
    }

    public IngestResult ingest(Observation observation) {
        try {
            validator.validate(observation);
        } catch (ObservationRejectedException e) {
            log.info("拒绝上报 recordId={} 原因={} 详情={}",
                    observation.recordId(), e.getReason(), e.getMessage());
            return IngestResult.rejected(e.getReason(), e.getMessage());
        }

        ReentrantLock lock = patientLocks.computeIfAbsent(observation.patientId(), k -> new ReentrantLock());
        lock.lock();
        try {
            if (!seenRecordIds.add(observation.recordId())) {
                log.info("重复上报幂等忽略 recordId={} patient={}",
                        observation.recordId(), observation.patientId());
                return IngestResult.duplicate(observation.recordId());
            }
            Observation stored = new Observation(
                    observation.recordId(), observation.patientId(), observation.type(),
                    observation.code(), observation.value(), observation.unit(),
                    observation.occurredAt(), Instant.now());
            observationsByPatient
                    .computeIfAbsent(stored.patientId(), k -> new CopyOnWriteArrayList<>())
                    .add(stored);

            RiskEvaluation evaluation = evaluateWithVersion(stored.patientId(), ruleRegistry.activeVersion());
            Optional<Alert> alert = alertService.applyEvaluation(evaluation);
            log.info("接受上报 recordId={} patient={} code={} value={} occurredAt={} -> 判定 {} 告警={}",
                    stored.recordId(), stored.patientId(), stored.code(), stored.value(),
                    stored.occurredAt(), evaluation.explain(),
                    alert.map(Alert::getAlertId).orElse("无"));
            return IngestResult.accepted(evaluation);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 用指定规则版本重放患者全部已存数据。同一版本 + 同一批数据必然得到相同结论，
     * 用于验证历史判定不受规则更新影响。
     */
    public RiskEvaluation replay(String patientId, String ruleVersion) {
        RuleVersion version = ruleRegistry.find(ruleVersion)
                .orElseThrow(() -> new IllegalArgumentException("规则版本不存在: " + ruleVersion));
        ReentrantLock lock = patientLocks.computeIfAbsent(patientId, k -> new ReentrantLock());
        lock.lock();
        try {
            return evaluateWithVersion(patientId, version);
        } finally {
            lock.unlock();
        }
    }

    private RiskEvaluation evaluateWithVersion(String patientId, RuleVersion version) {
        RiskEvaluation evaluation = riskEngine.evaluate(patientId, observations(patientId), version);
        evaluationLog.add(evaluation);
        return evaluation;
    }

    public List<Observation> observations(String patientId) {
        return observationsByPatient.getOrDefault(patientId, List.of()).stream()
                .sorted(Comparator.comparing(Observation::occurredAt)
                        .thenComparing(Observation::recordId))
                .toList();
    }

    public List<RiskEvaluation> evaluationLog() {
        return List.copyOf(evaluationLog);
    }

    public List<RiskEvaluation> evaluationLog(String patientId) {
        return evaluationLog.stream().filter(e -> e.patientId().equals(patientId)).toList();
    }
}

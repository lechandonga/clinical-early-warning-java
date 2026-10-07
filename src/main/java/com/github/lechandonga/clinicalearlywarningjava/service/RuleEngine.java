package com.github.lechandonga.clinicalearlywarningjava.service;

import com.github.lechandonga.clinicalearlywarningjava.domain.AlertSeverity;
import com.github.lechandonga.clinicalearlywarningjava.domain.Assessment;
import com.github.lechandonga.clinicalearlywarningjava.domain.Observation;
import com.github.lechandonga.clinicalearlywarningjava.domain.RuleCondition;
import com.github.lechandonga.clinicalearlywarningjava.domain.RuleVersion;
import com.github.lechandonga.clinicalearlywarningjava.domain.TriggerEvidence;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 纯函数式风险判定引擎：不依赖时钟与可变状态。
 * 给定规则版本与一批观测，输出确定的评分、严重度与逐条触发依据；
 * 同一规则版本重放同一批数据必然得到相同结论。
 */
@Component
public class RuleEngine {

    public Assessment evaluate(String patientId, Instant asOf, RuleVersion rules, List<Observation> observations) {
        Map<String, Observation> latestByMetric = latestByMetric(asOf, observations);

        List<TriggerEvidence> evidences = new ArrayList<>();
        int totalScore = 0;
        Map<String, List<RuleCondition>> conditionsByMetric = rules.conditions().stream()
                .collect(java.util.stream.Collectors.groupingBy(RuleCondition::metricCode));
        for (RuleCondition condition : rules.conditions()) {
            Observation latest = latestByMetric.get(condition.metricCode());
            if (latest == null) {
                continue;
            }
            if (condition.operator().matches(latest.value(), condition.threshold())) {
                TriggerEvidence evidence = new TriggerEvidence(
                        condition.metricCode(),
                        condition.operator().name(),
                        condition.threshold(),
                        latest.value(),
                        latest.observedAt(),
                        condition.score());
                int best = conditionsByMetric.get(condition.metricCode()).stream()
                        .filter(c -> c.operator().matches(latest.value(), c.threshold()))
                        .mapToInt(RuleCondition::score)
                        .max()
                        .orElse(0);
                if (condition.score() == best
                        && evidences.stream().noneMatch(e -> e.metricCode().equals(condition.metricCode()))) {
                    evidences.add(evidence);
                    totalScore += best;
                }
            }
        }
        evidences.sort(Comparator.comparing(TriggerEvidence::metricCode));

        AlertSeverity severity = classify(totalScore, rules);
        return new Assessment(patientId, asOf, rules.versionId(), totalScore, severity, evidences);
    }

    private AlertSeverity classify(int score, RuleVersion rules) {
        if (score >= rules.highScore()) {
            return AlertSeverity.HIGH;
        }
        if (score >= rules.mediumScore()) {
            return AlertSeverity.MEDIUM;
        }
        if (score > 0) {
            return AlertSeverity.LOW;
        }
        return null;
    }

    /** 取每个指标截至 asOf 的最新值；同一时间戳多条时按 recordId 确定性打破并列。 */
    private Map<String, Observation> latestByMetric(Instant asOf, List<Observation> observations) {
        Map<String, Observation> latest = new LinkedHashMap<>();
        for (Observation o : observations) {
            if (o.observedAt().isAfter(asOf)) {
                continue;
            }
            Observation current = latest.get(o.metricCode());
            if (current == null || isNewer(o, current)) {
                latest.put(o.metricCode(), o);
            }
        }
        return latest;
    }

    private boolean isNewer(Observation candidate, Observation current) {
        return Comparator.comparing(Observation::observedAt)
                .thenComparing(Observation::receivedAt)
                .thenComparing(Observation::recordId)
                .compare(candidate, current) > 0;
    }
}

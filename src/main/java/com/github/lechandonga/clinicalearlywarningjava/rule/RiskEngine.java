package com.github.lechandonga.clinicalearlywarningjava.rule;

import com.github.lechandonga.clinicalearlywarningjava.domain.Observation;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 纯函数式风险判定引擎：给定患者全部观测与某一规则版本，产出确定结论。
 * 同一批数据 + 同一规则版本 => 必然得到相同结论（按 occurredAt 取每个 code 最新值）。
 */
@Component
public class RiskEngine {

    public RiskEvaluation evaluate(String patientId, List<Observation> observations, RuleVersion ruleVersion) {
        Map<String, Observation> latestByCode = new HashMap<>();
        observations.stream()
                .sorted(Comparator.comparing(Observation::occurredAt)
                        .thenComparing(Observation::recordId))
                .forEach(o -> latestByCode.put(o.code(), o));

        int score = 0;
        List<RiskEvaluation.MatchedRule> matched = new ArrayList<>();
        for (ThresholdRule rule : ruleVersion.rules()) {
            Observation obs = latestByCode.get(rule.code());
            if (obs != null && rule.matches(obs.value())) {
                score += rule.score();
                matched.add(new RiskEvaluation.MatchedRule(
                        rule.code(), rule.description(), obs.value(), rule.threshold(),
                        rule.score(), obs.recordId(), obs.occurredAt()));
            }
        }
        return new RiskEvaluation(patientId, score, ruleVersion.levelForScore(score),
                List.copyOf(matched), ruleVersion.version(), Instant.now());
    }
}

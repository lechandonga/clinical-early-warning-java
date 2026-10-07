package com.github.lechandonga.clinicalearlywarningjava.rule;

import com.github.lechandonga.clinicalearlywarningjava.domain.RiskLevel;

import java.time.Instant;
import java.util.List;

/**
 * 一次风险判定的完整结论，含触发依据（命中的规则与当时的观测值），
 * 并记录所用规则版本，保证可解释与可重放。
 */
public record RiskEvaluation(
        String patientId,
        int score,
        RiskLevel level,
        List<MatchedRule> matchedRules,
        String ruleVersion,
        Instant evaluatedAt) {

    public record MatchedRule(
            String code,
            String description,
            double observedValue,
            double threshold,
            int score,
            String observationRecordId,
            Instant observedAt) {

        public String explain() {
            return "%s: %s=%.1f (阈值 %.1f, +%d分, 记录 %s @ %s)"
                    .formatted(description, code, observedValue, threshold, score,
                            observationRecordId, observedAt);
        }
    }

    public String explain() {
        if (matchedRules.isEmpty()) {
            return "未命中任何风险规则, 总分 0, 等级 " + level;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("规则版本 ").append(ruleVersion)
          .append(", 总分 ").append(score).append(", 等级 ").append(level).append("; 命中规则: ");
        matchedRules.forEach(m -> sb.append('[').append(m.explain()).append(']'));
        return sb.toString();
    }
}

package com.github.lechandonga.clinicalearlywarningjava.domain;

import java.time.Instant;
import java.util.List;

/**
 * 一次风险评估结果。评估是纯函数产物：同一规则版本 + 同一批观测必然得到相同结果。
 */
public record Assessment(
        String patientId,
        Instant evaluatedAt,
        String ruleVersionId,
        int totalScore,
        AlertSeverity severity,
        List<TriggerEvidence> evidences
) {
    public Assessment {
        evidences = List.copyOf(evidences);
    }
}

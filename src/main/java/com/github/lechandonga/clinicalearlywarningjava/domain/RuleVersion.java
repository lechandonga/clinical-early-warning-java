package com.github.lechandonga.clinicalearlywarningjava.domain;

import java.time.Instant;
import java.util.List;

/**
 * 规则版本。版本一经发布即不可变；effectiveFrom 决定该版本适用于哪些观测时间点的数据，
 * 从而保证迟到数据使用“当时生效”的规则，历史结论不随规则更新被改写。
 */
public record RuleVersion(
        String versionId,
        String description,
        Instant createdAt,
        Instant effectiveFrom,
        List<RuleCondition> conditions,
        int mediumScore,
        int highScore
) {
    public RuleVersion {
        conditions = List.copyOf(conditions);
    }

    public boolean effectiveAt(Instant t) {
        return !t.isBefore(effectiveFrom);
    }
}

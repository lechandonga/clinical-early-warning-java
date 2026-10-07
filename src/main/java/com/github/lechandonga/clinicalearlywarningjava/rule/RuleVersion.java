package com.github.lechandonga.clinicalearlywarningjava.rule;

import com.github.lechandonga.clinicalearlywarningjava.domain.RiskLevel;

import java.time.Instant;
import java.util.List;
import java.util.TreeMap;

/**
 * 不可变的规则版本。规则一旦发布不可修改，更新规则即产生新版本，
 * 历史判定与告警永远保留其产生时使用的版本号。
 *
 * @param version           版本号，如 "v1"
 * @param rules             阈值规则列表
 * @param levelBands        分值 -> 风险等级 的分段（key 为下限分，升序）
 * @param alertFromLevel    达到该等级即产生告警
 * @param escalateOnIncrease 已确认告警风险继续升高时是否再次提醒
 * @param createdAt         版本创建时间
 */
public record RuleVersion(
        String version,
        List<ThresholdRule> rules,
        TreeMap<Integer, RiskLevel> levelBands,
        RiskLevel alertFromLevel,
        boolean escalateOnIncrease,
        Instant createdAt) {

    public RuleVersion {
        rules = List.copyOf(rules);
        levelBands = new TreeMap<>(levelBands);
    }

    public RiskLevel levelForScore(int score) {
        var entry = levelBands.floorEntry(score);
        return entry == null ? RiskLevel.LOW : entry.getValue();
    }
}

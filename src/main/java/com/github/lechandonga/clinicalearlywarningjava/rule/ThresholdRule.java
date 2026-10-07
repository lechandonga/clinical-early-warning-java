package com.github.lechandonga.clinicalearlywarningjava.rule;

/**
 * 单指标阈值规则：当某观测项目的最新值满足条件时贡献相应分值。
 *
 * @param code        观测项目编码，如 HR、SPO2
 * @param operator    比较符：GT / GTE / LT / LTE
 * @param threshold   阈值
 * @param score       命中后累加的风险分
 * @param description 人类可读的规则说明，用于告警可解释性
 */
public record ThresholdRule(
        String code,
        Operator operator,
        double threshold,
        int score,
        String description) {

    public enum Operator {
        GT, GTE, LT, LTE
    }

    public boolean matches(double value) {
        return switch (operator) {
            case GT -> value > threshold;
            case GTE -> value >= threshold;
            case LT -> value < threshold;
            case LTE -> value <= threshold;
        };
    }
}

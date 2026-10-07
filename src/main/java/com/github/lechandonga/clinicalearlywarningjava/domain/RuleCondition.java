package com.github.lechandonga.clinicalearlywarningjava.domain;

/**
 * 单条规则条件：指标 + 运算符 + 阈值 + 命中分值。
 */
public record RuleCondition(
        String metricCode,
        Operator operator,
        double threshold,
        int score
) {
}

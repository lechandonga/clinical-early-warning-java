package com.github.lechandonga.clinicalearlywarningjava.domain;

/**
 * 规则条件比较运算符。
 */
public enum Operator {
    LT,
    LTE,
    GT,
    GTE;

    public boolean matches(double value, double threshold) {
        return switch (this) {
            case LT -> value < threshold;
            case LTE -> value <= threshold;
            case GT -> value > threshold;
            case GTE -> value >= threshold;
        };
    }
}

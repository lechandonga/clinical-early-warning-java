package com.github.lechandonga.clinicalearlywarningjava.domain;

/**
 * 指标定义：类型与合法取值范围（用于上报数据校验，与规则版本无关）。
 */
public record MetricDef(String code, MetricType type, double min, double max) {
}

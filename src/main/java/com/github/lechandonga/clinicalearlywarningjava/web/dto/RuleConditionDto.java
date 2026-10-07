package com.github.lechandonga.clinicalearlywarningjava.web.dto;

import com.github.lechandonga.clinicalearlywarningjava.domain.Operator;

public class RuleConditionDto {
    private String metricCode;
    private Operator operator;
    private double threshold;
    private int score;

    public String getMetricCode() { return metricCode; }
    public void setMetricCode(String metricCode) { this.metricCode = metricCode; }
    public Operator getOperator() { return operator; }
    public void setOperator(Operator operator) { this.operator = operator; }
    public double getThreshold() { return threshold; }
    public void setThreshold(double threshold) { this.threshold = threshold; }
    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
}

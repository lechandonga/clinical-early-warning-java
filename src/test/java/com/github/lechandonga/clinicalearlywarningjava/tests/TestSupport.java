package com.github.lechandonga.clinicalearlywarningjava.tests;

import com.github.lechandonga.clinicalearlywarningjava.alert.AlertService;
import com.github.lechandonga.clinicalearlywarningjava.config.EarlyWarningProperties;
import com.github.lechandonga.clinicalearlywarningjava.domain.Observation;
import com.github.lechandonga.clinicalearlywarningjava.domain.ObservationType;
import com.github.lechandonga.clinicalearlywarningjava.domain.RiskLevel;
import com.github.lechandonga.clinicalearlywarningjava.ingest.ObservationService;
import com.github.lechandonga.clinicalearlywarningjava.rule.RiskEngine;
import com.github.lechandonga.clinicalearlywarningjava.rule.RuleRegistry;
import com.github.lechandonga.clinicalearlywarningjava.rule.RuleVersion;
import com.github.lechandonga.clinicalearlywarningjava.rule.ThresholdRule;
import com.github.lechandonga.clinicalearlywarningjava.validation.ObservationValidator;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 测试支撑：用固定时钟与内存组件搭建完整服务栈。
 */
final class TestSupport {

    static final Instant NOW = Instant.parse("2026-10-07T08:00:00Z");

    static final class Stack {
        final RuleRegistry ruleRegistry = new RuleRegistry();
        final AlertService alertService = new AlertService(ruleRegistry);
        final ObservationService observationService;

        Stack() {
            EarlyWarningProperties props = new EarlyWarningProperties();
            props.setRanges(Map.of(
                    "HR", new EarlyWarningProperties.Range(0, 250),
                    "SBP", new EarlyWarningProperties.Range(30, 300),
                    "SPO2", new EarlyWarningProperties.Range(0, 100),
                    "LACTATE", new EarlyWarningProperties.Range(0, 30)));
            props.setMaxFutureSkew(Duration.ofMinutes(5));
            props.setMaxPastAge(Duration.ofDays(30));
            ObservationValidator validator = new ObservationValidator(
                    props, Clock.fixed(NOW, ZoneOffset.UTC));
            this.observationService = new ObservationService(
                    validator, new RiskEngine(), ruleRegistry, alertService);
        }

        RuleVersion registerV1() {
            RuleVersion v1 = new RuleVersion("v1",
                    List.of(
                            new ThresholdRule("HR", ThresholdRule.Operator.GT, 120, 2, "心率 > 120"),
                            new ThresholdRule("SBP", ThresholdRule.Operator.LT, 90, 2, "收缩压 < 90"),
                            new ThresholdRule("SPO2", ThresholdRule.Operator.LT, 92, 2, "血氧 < 92"),
                            new ThresholdRule("LACTATE", ThresholdRule.Operator.GT, 2.0, 2, "乳酸 > 2.0")),
                    new TreeMap<>(Map.of(0, RiskLevel.LOW, 2, RiskLevel.MEDIUM, 4, RiskLevel.HIGH, 6, RiskLevel.CRITICAL)),
                    RiskLevel.MEDIUM, true, NOW);
            ruleRegistry.register(v1, true);
            return v1;
        }
    }

    static Observation obs(String recordId, String patientId, String code, double value, Instant occurredAt) {
        return new Observation(recordId, patientId, ObservationType.VITAL_SIGN,
                code, value, "", occurredAt, null);
    }

    static Observation obs(String recordId, String patientId, String code, double value) {
        return obs(recordId, patientId, code, value, NOW);
    }
}

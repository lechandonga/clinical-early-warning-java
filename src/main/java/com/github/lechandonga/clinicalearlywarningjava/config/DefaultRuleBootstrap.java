package com.github.lechandonga.clinicalearlywarningjava.config;

import com.github.lechandonga.clinicalearlywarningjava.domain.RiskLevel;
import com.github.lechandonga.clinicalearlywarningjava.rule.RuleRegistry;
import com.github.lechandonga.clinicalearlywarningjava.rule.RuleVersion;
import com.github.lechandonga.clinicalearlywarningjava.rule.ThresholdRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.List;
import java.util.TreeMap;

/**
 * 注册内置默认规则版本 v1，作为本地模拟的起点。
 */
@Configuration
@EnableConfigurationProperties(EarlyWarningProperties.class)
public class DefaultRuleBootstrap {

    private static final Logger log = LoggerFactory.getLogger(DefaultRuleBootstrap.class);

    private final RuleRegistry ruleRegistry;

    public DefaultRuleBootstrap(RuleRegistry ruleRegistry) {
        this.ruleRegistry = ruleRegistry;
    }

    @PostConstruct
    public void registerDefaultRules() {
        RuleVersion v1 = new RuleVersion(
                "v1",
                List.of(
                        new ThresholdRule("HR", ThresholdRule.Operator.GT, 120, 2, "心率 > 120"),
                        new ThresholdRule("HR", ThresholdRule.Operator.LT, 40, 2, "心率 < 40"),
                        new ThresholdRule("SBP", ThresholdRule.Operator.LT, 90, 2, "收缩压 < 90"),
                        new ThresholdRule("SPO2", ThresholdRule.Operator.LT, 92, 2, "血氧饱和度 < 92"),
                        new ThresholdRule("RR", ThresholdRule.Operator.GT, 24, 1, "呼吸频率 > 24"),
                        new ThresholdRule("TEMP", ThresholdRule.Operator.GT, 38.5, 1, "体温 > 38.5"),
                        new ThresholdRule("LACTATE", ThresholdRule.Operator.GT, 2.0, 2, "乳酸 > 2.0"),
                        new ThresholdRule("WBC", ThresholdRule.Operator.GT, 12.0, 1, "白细胞 > 12.0")
                ),
                new TreeMap<>(java.util.Map.of(
                        0, RiskLevel.LOW,
                        2, RiskLevel.MEDIUM,
                        4, RiskLevel.HIGH,
                        6, RiskLevel.CRITICAL)),
                RiskLevel.MEDIUM,
                true,
                Instant.now());
        ruleRegistry.register(v1, true);
        log.info("注册默认规则版本 {} ({} 条阈值规则)", v1.version(), v1.rules().size());
    }
}

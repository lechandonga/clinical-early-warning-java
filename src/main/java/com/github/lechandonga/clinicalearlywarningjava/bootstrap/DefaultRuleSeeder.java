package com.github.lechandonga.clinicalearlywarningjava.bootstrap;

import com.github.lechandonga.clinicalearlywarningjava.domain.Operator;
import com.github.lechandonga.clinicalearlywarningjava.domain.RuleCondition;
import com.github.lechandonga.clinicalearlywarningjava.service.RuleService;
import com.github.lechandonga.clinicalearlywarningjava.store.RuleStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * 启动时内置一版 NEWS 风格默认规则（v1，自 2000 年起生效，覆盖所有历史数据）。
 * 评分阈值：>=4 MEDIUM，>=8 HIGH。可通过 API 继续发布 v2 等新版本。
 */
@Component
public class DefaultRuleSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultRuleSeeder.class);

    private final RuleService ruleService;
    private final RuleStore ruleStore;

    public DefaultRuleSeeder(RuleService ruleService, RuleStore ruleStore) {
        this.ruleService = ruleService;
        this.ruleStore = ruleStore;
    }

    @Override
    public void run(String... args) {
        if (ruleStore.get("v1").isPresent()) {
            return;
        }
        List<RuleCondition> v1 = List.of(
                new RuleCondition("RESP_RATE", Operator.LTE, 8, 3),
                new RuleCondition("RESP_RATE", Operator.GTE, 25, 3),
                new RuleCondition("O2_SAT", Operator.LTE, 91, 3),
                new RuleCondition("O2_SAT", Operator.LTE, 94, 2),
                new RuleCondition("TEMP_C", Operator.LTE, 35.0, 3),
                new RuleCondition("TEMP_C", Operator.GTE, 39.1, 2),
                new RuleCondition("SYS_BP", Operator.LTE, 90, 3),
                new RuleCondition("SYS_BP", Operator.GTE, 220, 2),
                new RuleCondition("HEART_RATE", Operator.LTE, 40, 3),
                new RuleCondition("HEART_RATE", Operator.GTE, 131, 3),
                new RuleCondition("HEART_RATE", Operator.GTE, 111, 2),
                new RuleCondition("CONSCIOUS", Operator.LTE, 0, 3),
                new RuleCondition("LACTATE", Operator.GTE, 4.0, 3),
                new RuleCondition("LACTATE", Operator.GTE, 2.0, 1)
        );
        ruleService.publish("v1", "内置 NEWS 风格默认规则", Instant.parse("2000-01-01T00:00:00Z"),
                v1, 4, 8);
        log.info("已发布默认风险规则版本 v1（条件 {} 条，MEDIUM>=4，HIGH>=8）", v1.size());
    }
}

package com.github.lechandonga.clinicalearlywarningjava.tests;

import com.github.lechandonga.clinicalearlywarningjava.domain.RiskLevel;
import com.github.lechandonga.clinicalearlywarningjava.ingest.IngestResult;
import com.github.lechandonga.clinicalearlywarningjava.rule.RiskEvaluation;
import com.github.lechandonga.clinicalearlywarningjava.rule.RuleVersion;
import com.github.lechandonga.clinicalearlywarningjava.rule.ThresholdRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * 业务场景：规则版本切换后历史判定不被改写，用当时的版本重放得到相同结论。
 */
@DisplayName("规则版本切换场景")
class RuleVersionSwitchTest {

    private static final Logger log = LoggerFactory.getLogger(RuleVersionSwitchTest.class);

    private final TestSupport.Stack stack = new TestSupport.Stack();

    @Test
    @DisplayName("切换规则版本：历史判定保留旧版本结论，重放一致，新判定用新版本")
    void versionSwitchKeepsHistoryAndReplayConsistent() {
        stack.registerV1();
        // HR=115：v1 阈值 120 不命中 -> LOW
        IngestResult before = stack.observationService.ingest(TestSupport.obs("r1", "P1", "HR", 115));
        assertEquals(RiskLevel.LOW, before.evaluation().level());
        assertEquals("v1", before.evaluation().ruleVersion());

        // v2：HR 阈值收紧到 110
        RuleVersion v2 = new RuleVersion("v2",
                List.of(new ThresholdRule("HR", ThresholdRule.Operator.GT, 110, 2, "心率 > 110")),
                new TreeMap<>(Map.of(0, RiskLevel.LOW, 2, RiskLevel.MEDIUM, 4, RiskLevel.HIGH)),
                RiskLevel.MEDIUM, true, TestSupport.NOW.plusSeconds(1));
        stack.ruleRegistry.register(v2, true);

        // 历史判定日志不被改写
        RiskEvaluation historical = stack.observationService.evaluationLog("P1").get(0);
        assertEquals("v1", historical.ruleVersion());
        assertEquals(RiskLevel.LOW, historical.level());

        // 用当时的 v1 重放同一批数据 -> 相同结论
        RiskEvaluation replayV1 = stack.observationService.replay("P1", "v1");
        // 用 v2 重放 -> 结论不同，证明版本确实影响判定
        RiskEvaluation replayV2 = stack.observationService.replay("P1", "v2");
        log.info("用例[版本切换] 历史判定={}/{} v1重放={}/{} v2重放={}/{}",
                historical.ruleVersion(), historical.level(),
                replayV1.ruleVersion(), replayV1.level(),
                replayV2.ruleVersion(), replayV2.level());
        assertEquals(historical.level(), replayV1.level());
        assertEquals(historical.score(), replayV1.score());
        assertNotEquals(replayV1.level(), replayV2.level());

        // 新数据按激活的 v2 判定
        IngestResult after = stack.observationService.ingest(TestSupport.obs("r2", "P1", "HR", 115));
        assertEquals("v2", after.evaluation().ruleVersion());
        assertEquals(RiskLevel.MEDIUM, after.evaluation().level());
    }

    @Test
    @DisplayName("规则不可变：重复注册同名版本被拒绝")
    void ruleVersionIsImmutable() {
        stack.registerV1();
        RuleVersion dup = new RuleVersion("v1", List.of(),
                new TreeMap<>(Map.of(0, RiskLevel.LOW)), RiskLevel.MEDIUM, true, TestSupport.NOW);
        IllegalArgumentException e = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> stack.ruleRegistry.register(dup, false));
        log.info("用例[规则不可变] 拒绝原因={}", e.getMessage());
    }
}

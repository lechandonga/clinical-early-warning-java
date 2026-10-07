package com.github.lechandonga.clinicalearlywarningjava.tests;

import com.github.lechandonga.clinicalearlywarningjava.domain.Alert;
import com.github.lechandonga.clinicalearlywarningjava.domain.Assessment;
import com.github.lechandonga.clinicalearlywarningjava.domain.Operator;
import com.github.lechandonga.clinicalearlywarningjava.domain.RuleCondition;
import com.github.lechandonga.clinicalearlywarningjava.domain.RuleVersion;
import com.github.lechandonga.clinicalearlywarningjava.service.ObservationService;
import com.github.lechandonga.clinicalearlywarningjava.service.ReplayService;
import com.github.lechandonga.clinicalearlywarningjava.service.RuleEngine;
import com.github.lechandonga.clinicalearlywarningjava.service.RuleService;
import com.github.lechandonga.clinicalearlywarningjava.store.AlertStore;
import com.github.lechandonga.clinicalearlywarningjava.store.ObservationStore;
import com.github.lechandonga.clinicalearlywarningjava.store.RuleStore;
import com.github.lechandonga.clinicalearlywarningjava.tests.support.TestClockConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 业务：规则版本切换。
 * - 规则版本只增不改，按生效时间适用于对应观测时刻的数据；
 * - 规则更新后历史评估 / 已产生告警保持旧版本结论不被改写；
 * - 用当时版本重放同一批数据结论完全一致；同版本重放确定可复现，不同版本结论可不同。
 */
@SpringBootTest
@Import(TestClockConfig.class)
class RuleVersionSwitchingTest {

    private static final Logger log = LoggerFactory.getLogger(RuleVersionSwitchingTest.class);

    @Autowired
    private ObservationService observationService;
    @Autowired
    private RuleService ruleService;
    @Autowired
    private RuleStore ruleStore;
    @Autowired
    private RuleEngine ruleEngine;
    @Autowired
    private ReplayService replayService;
    @Autowired
    private ObservationStore observationStore;
    @Autowired
    private AlertStore alertStore;

    @Test
    @DisplayName("发布 v2 后: 历史数据仍按 v1，迟到的历史数据也用 v1；新数据按 v2；历史结论不改写")
    void versionSwitchKeepsHistoryAndAppliesByEffectiveTime() {
        Instant historyT = Instant.parse("2026-10-07T15:00:00Z");
        Instant v2From = Instant.parse("2026-10-07T16:00:00Z");
        Instant newT = v2From.plusSeconds(60);

        observationService.ingest("V-1", "P-RULE", "O2_SAT", 92.0, historyT);
        Assessment historyAssessment = alertStore.listAssessments("P-RULE").get(0);
        assertEquals("v1", historyAssessment.ruleVersionId());
        assertEquals(2, historyAssessment.totalScore(), "v1 中 91<SpO2<=94 计 2 分");

        List<RuleCondition> v2Conditions = List.of(
                new RuleCondition("O2_SAT", Operator.LTE, 90.0, 3),
                new RuleCondition("SYS_BP", Operator.LTE, 100.0, 3)
        );
        RuleVersion v2 = ruleService.publish("v2", "收紧氧合阈值并新增低血压条件", v2From,
                v2Conditions, 3, 6);
        log.info("[规则版本] 发布 v2(effectiveFrom={}) => 测试通过", v2From);

        Alert historyAlert = alertStore.listByPatient("P-RULE").get(0);
        assertEquals("v1", historyAlert.getRuleVersionId(), "已产生告警绑定的规则版本不得被改写");
        assertEquals(2, historyAlert.getInitialScore());

        var lateHistory = observationService.ingest("V-2", "P-RULE", "DIA_BP", 50.0,
                historyT.plusSeconds(30));
        assertEquals("v1", lateHistory.assessment().ruleVersionId(),
                "观测时刻早于 v2 生效点的迟到数据，必须仍按 v1 判定");
        assertEquals(2, lateHistory.assessment().totalScore(), "v1/v2 均未配置 DIA_BP 条件，分数应保持 2");
        log.info("[规则版本] 迟到的历史数据(DIA_BP=50@{}) => 测试通过: 仍用 v1 且分数={}",
                historyT.plusSeconds(30), lateHistory.assessment().totalScore());

        var afterV2 = observationService.ingest("V-3", "P-RULE", "O2_SAT", 92.0, newT);
        assertEquals("v2", afterV2.assessment().ruleVersionId(), "v2 生效后的数据必须按 v2 判定");
        assertEquals(0, afterV2.assessment().totalScore(), "v2 下 SpO2=92 不命中任何条件");
        log.info("[规则版本] v2 生效后的 O2_SAT=92 => 测试通过: 使用 v2 且分数=0，新旧结论共存");

        Assessment replayV1 = replayService.replayAt("P-RULE", historyT.plusSeconds(60), "v1");
        Assessment replayV1Again = replayService.replayAt("P-RULE", historyT.plusSeconds(60), "v1");
        assertEquals(historyAssessment.totalScore(), replayV1.totalScore());
        assertEquals("v1", replayV1.ruleVersionId());
        assertEquals(replayV1.totalScore(), replayV1Again.totalScore());
        assertEquals(replayV1.evidences(), replayV1Again.evidences(), "同版本重放触发依据也必须一致");

        Assessment replayV2AtSameData = replayService.replayAt("P-RULE", historyT.plusSeconds(60), "v2");
        assertEquals("v2", replayV2AtSameData.ruleVersionId());
        assertNotEquals(replayV1.totalScore(), replayV2AtSameData.totalScore(),
                "同批数据用不同版本重放应可得到不同结论，证明版本确实影响判定");
        log.info("[规则版本] 重放一致性 => 测试通过: v1重放={}分(可复现) vs v2重放={}分",
                replayV1.totalScore(), replayV2AtSameData.totalScore());

        Assessment replayDefault = replayService.replayAt("P-RULE", newT, null);
        assertEquals("v2", replayDefault.ruleVersionId(), "不指定版本时应选择观测时刻生效的版本");
        assertTrue(ruleStore.all().stream().map(RuleVersion::versionId).toList().containsAll(List.of("v1", "v2")));
    }

    @Test
    @DisplayName("规则引擎纯函数: 相同输入多次评估输出逐字段一致（含触发依据顺序）")
    void engineIsDeterministic() {
        Instant t = Instant.parse("2026-10-07T17:00:00Z");
        RuleVersion v1 = ruleStore.get("v1").orElseThrow();
        Assessment a = ruleEngine.evaluate("P-RULE", t, v1,
                observationStore.snapshotAt("P-RULE", t));
        Assessment b = ruleEngine.evaluate("P-RULE", t, v1,
                observationStore.snapshotAt("P-RULE", t));
        assertEquals(a, b);
        log.info("[规则版本] 纯函数确定性 => 测试通过: 评分={} 严重度={} 依据条数={}",
                a.totalScore(), a.severity(), a.evidences().size());
    }
}

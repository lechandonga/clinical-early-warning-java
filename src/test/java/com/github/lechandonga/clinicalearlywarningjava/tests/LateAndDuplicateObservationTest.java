package com.github.lechandonga.clinicalearlywarningjava.tests;

import com.github.lechandonga.clinicalearlywarningjava.domain.Alert;
import com.github.lechandonga.clinicalearlywarningjava.domain.AlertStatus;
import com.github.lechandonga.clinicalearlywarningjava.domain.Observation;
import com.github.lechandonga.clinicalearlywarningjava.service.ObservationService;
import com.github.lechandonga.clinicalearlywarningjava.store.AlertStore;
import com.github.lechandonga.clinicalearlywarningjava.store.ObservationStore;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 业务：乱序 / 迟到 / 幂等重复上报。
 * - 迟到数据必须纳入判定并能触发 / 升级告警；
 * - 同一 (指标, 时间点) 的重复上报不得产生新数据或重复告警；
 * - 查询结果按观测时间有序，迟到数据已并入正确位置。
 */
@SpringBootTest
@Import(TestClockConfig.class)
class LateAndDuplicateObservationTest {

    private static final Logger log = LoggerFactory.getLogger(LateAndDuplicateObservationTest.class);

    @Autowired
    private ObservationService observationService;
    @Autowired
    private ObservationStore observationStore;
    @Autowired
    private AlertStore alertStore;

    @Test
    @DisplayName("乱序到达: 后到的早时间数据(迟到)纳入判定并触发告警；重复内容幂等")
    void outOfOrderAndLateDataAreUsed() {
        Instant t0 = Instant.parse("2026-10-07T08:00:00Z");
        Instant t1 = t0.plusSeconds(60);
        Instant t2 = t0.plusSeconds(120);

        observationService.ingest("L-1", "P-LATE", "TEMP_C", 37.0, t1);
        observationService.ingest("L-2", "P-LATE", "HEART_RATE", 80.0, t2);
        assertTrue(alertStore.findOpen("P-LATE").isEmpty(),
                "正常数据不应产生告警");

        var late = observationService.ingest("L-3", "P-LATE", "O2_SAT", 88.0, t0);
        assertTrue(late.accepted(), "迟到数据必须被接受");
        assertTrue(late.assessment().totalScore() >= 3, "迟到低氧必须纳入判定: " + late.assessment());
        Alert alert = alertStore.findOpen("P-LATE").orElseThrow();
        assertEquals(AlertStatus.ACTIVE, alert.getStatus());
        log.info("[迟到上报] 迟到的 O2_SAT=88（t0）在 t2 后到达 => 测试通过: 评分={}, 已产生告警 {}",
                late.assessment().totalScore(), alert.getId());

        List<Observation> stored = observationStore.listByPatient("P-LATE");
        assertEquals(List.of(t0, t1, t2), stored.stream().map(Observation::observedAt).toList());

        var sameContent = observationService.ingest("L-4", "P-LATE", "O2_SAT", 88.0, t0);
        assertTrue(sameContent.duplicated(), "同(指标,时间点)数据应标记为幂等重复");
        assertEquals(3, observationStore.listByPatient("P-LATE").size(), "重复内容不得新增观测");
        assertEquals(1, alertStore.listByPatient("P-LATE").size(), "重复触发不得产生重复告警");
        log.info("[重复上报] 相同(指标,时间点)再次上报 => 测试通过: 幂等合并，不新增告警");

        observationService.ingest("L-5", "P-LATE", "SYS_BP", 80.0, t0.plusSeconds(30));
        Alert open = alertStore.findOpen("P-LATE").orElseThrow();
        assertTrue(open.getLastScore() >= 5, "更多异常数据应抬高评分并作用于同一告警");
        assertEquals(1, alertStore.listByPatient("P-LATE").size(), "风险升高不得另开告警，应在原告警升级");
        assertEquals(AlertStatus.ESCALATED, open.getStatus());
        log.info("[迟到上报] 再补一条迟到低血压 => 测试通过: 同告警升级 ESCALATED，评分={}", open.getLastScore());
    }

    @Test
    @DisplayName("风险未升高时重复触发不重复告警")
    void noDuplicateAlertWhenRiskUnchanged() {
        Instant t0 = Instant.parse("2026-10-07T09:00:00Z");
        observationService.ingest("N-1", "P-NOISE", "O2_SAT", 90.0, t0);
        observationService.ingest("N-2", "P-NOISE", "TEMP_C", 34.0, t0.plusSeconds(10));
        observationService.ingest("N-3", "P-NOISE", "HEART_RATE", 85.0, t0.plusSeconds(20));
        assertEquals(1, alertStore.listByPatient("P-NOISE").size());
        Alert open = alertStore.findOpen("P-NOISE").orElseThrow();
        assertFalse(open.getEvents().isEmpty());
        log.info("[重复上报] 多条上报风险持平 => 测试通过: 仅 1 条告警，无重复产生");
    }
}

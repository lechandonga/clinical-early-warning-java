package com.github.lechandonga.clinicalearlywarningjava.tests;

import com.github.lechandonga.clinicalearlywarningjava.domain.Alert;
import com.github.lechandonga.clinicalearlywarningjava.domain.AlertAction;
import com.github.lechandonga.clinicalearlywarningjava.domain.AlertStatus;
import com.github.lechandonga.clinicalearlywarningjava.exception.BusinessException;
import com.github.lechandonga.clinicalearlywarningjava.service.AlertService;
import com.github.lechandonga.clinicalearlywarningjava.service.ObservationService;
import com.github.lechandonga.clinicalearlywarningjava.store.AlertStore;
import com.github.lechandonga.clinicalearlywarningjava.tests.support.TestClockConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 业务：告警状态流转闭环。
 * 合法：产生 -> 确认 -> (风险继续升高)再提醒升级 -> 解除；
 * 非法：确认后再确认、解除后任何操作、无操作人等，必须被拒绝并说明原因。
 */
@SpringBootTest
@Import(TestClockConfig.class)
class AlertLifecycleTest {

    private static final Logger log = LoggerFactory.getLogger(AlertLifecycleTest.class);

    @Autowired
    private ObservationService observationService;
    @Autowired
    private AlertService alertService;
    @Autowired
    private AlertStore alertStore;

    @Test
    @DisplayName("合法闭环: 产生 -> 确认 -> 风险升高再提醒 -> 解除，且轨迹可解释")
    void legalLifecycleAndReAlertAfterAcknowledge() {
        Instant t0 = Instant.parse("2026-10-07T10:00:00Z");
        observationService.ingest("A-1", "P-ALERT", "O2_SAT", 90.0, t0);
        Alert alert = alertStore.findOpen("P-ALERT").orElseThrow();
        assertEquals(AlertStatus.ACTIVE, alert.getStatus());
        assertFalse(alert.getInitialEvidence().isEmpty(), "告警必须携带触发依据");
        log.info("[告警流转] 用例-产生 => 测试通过: 触发依据={}", alert.getInitialEvidence());

        alertService.transition(alert.getId(), AlertAction.ACKNOWLEDGE, "护士小王", "已床旁评估");
        assertEquals(AlertStatus.ACKNOWLEDGED, alertStore.findById(alert.getId()).orElseThrow().getStatus());
        log.info("[告警流转] 用例-确认 => 测试通过: ACTIVE -> ACKNOWLEDGED");

        observationService.ingest("A-2", "P-ALERT", "SYS_BP", 80.0, t0.plusSeconds(30));
        Alert after = alertStore.findById(alert.getId()).orElseThrow();
        assertEquals(AlertStatus.ESCALATED, after.getStatus(), "已确认患者风险继续升高必须再次提醒");
        assertTrue(after.getEvents().stream().anyMatch(e -> "RE_ALERT_ESCALATE".equals(e.action())),
                "应留下再提醒事件");
        assertEquals(1, alertStore.listByPatient("P-ALERT").size(), "再提醒不得新开告警");
        log.info("[告警流转] 用例-确认后再提醒 => 测试通过: ACKNOWLEDGED -> ESCALATED (RE_ALERT_ESCALATE)");

        Alert resolved = alertService.transition(alert.getId(), AlertAction.RESOLVE, "李医生", "病情稳定");
        assertEquals(AlertStatus.RESOLVED, resolved.getStatus());
        assertTrue(alertStore.findOpen("P-ALERT").isEmpty());
        assertTrue(resolved.getEvents().size() >= 4, "完整轨迹应包含产生/确认/升级/解除");
        log.info("[告警流转] 用例-解除 => 测试通过: ESCALATED -> RESOLVED，轨迹事件数={}",
                resolved.getEvents().size());
    }

    @Test
    @DisplayName("非法流转全部拒绝: 重复确认 / 解除后再操作 / 无操作人 / 告警不存在")
    void illegalTransitionsAreRejected() {
        Instant t0 = Instant.parse("2026-10-07T11:00:00Z");
        observationService.ingest("B-1", "P-ILLEGAL", "TEMP_C", 34.0, t0);
        Alert alert = alertStore.findOpen("P-ILLEGAL").orElseThrow();

        alertService.transition(alert.getId(), AlertAction.ACKNOWLEDGE, "小王", null);
        BusinessException doubleAck = assertThrows(BusinessException.class, () ->
                alertService.transition(alert.getId(), AlertAction.ACKNOWLEDGE, "小王", "再确认一次"));
        assertEquals("ILLEGAL_TRANSITION", doubleAck.getCode());
        log.info("[告警流转] 用例-非法重复确认 => 测试通过: {}", doubleAck.getMessage());

        BusinessException noOperator = assertThrows(BusinessException.class, () ->
                alertService.transition(alert.getId(), AlertAction.ESCALATE, "  ", null));
        assertEquals("OPERATOR_REQUIRED", noOperator.getCode());
        log.info("[告警流转] 用例-缺少操作人 => 测试通过: {}", noOperator.getMessage());

        alertService.transition(alert.getId(), AlertAction.RESOLVE, "李医生", null);
        BusinessException resolveAgain = assertThrows(BusinessException.class, () ->
                alertService.transition(alert.getId(), AlertAction.RESOLVE, "李医生", null));
        BusinessException escalateResolved = assertThrows(BusinessException.class, () ->
                alertService.transition(alert.getId(), AlertAction.ESCALATE, "李医生", null));
        assertEquals("ILLEGAL_TRANSITION", resolveAgain.getCode());
        assertEquals("ILLEGAL_TRANSITION", escalateResolved.getCode());
        log.info("[告警流转] 用例-解除后再解除/再升级 => 测试通过: [{}] [{}]",
                resolveAgain.getMessage(), escalateResolved.getMessage());

        BusinessException notFound = assertThrows(BusinessException.class, () ->
                alertService.transition("AL-NOT-EXIST", AlertAction.RESOLVE, "李医生", null));
        assertEquals("ALERT_NOT_FOUND", notFound.getCode());
        log.info("[告警流转] 用例-告警不存在 => 测试通过: code=ALERT_NOT_FOUND");
    }

    @Test
    @DisplayName("解除后出现新风险应产生新告警，与历史解除告警并存且不被改写")
    void newRiskAfterResolveCreatesNewAlert() {
        Instant t0 = Instant.parse("2026-10-07T12:00:00Z");
        observationService.ingest("C-1", "P-RECOVER", "O2_SAT", 89.0, t0);
        Alert first = alertStore.findOpen("P-RECOVER").orElseThrow();
        alertService.transition(first.getId(), AlertAction.RESOLVE, "李医生", "好转");

        observationService.ingest("C-2", "P-RECOVER", "LACTATE", 5.0, t0.plusSeconds(120));
        assertEquals(2, alertStore.listByPatient("P-RECOVER").size());
        assertEquals(AlertStatus.RESOLVED,
                alertStore.listByPatient("P-RECOVER").get(0).getStatus(), "历史告警状态不得被改写");
        log.info("[告警流转] 用例-解除后新发风险 => 测试通过: 历史 RESOLVED 保留，新告警 ACTIVE");
    }
}

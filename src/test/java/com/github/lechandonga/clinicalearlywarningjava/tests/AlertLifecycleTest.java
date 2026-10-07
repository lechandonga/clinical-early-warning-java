package com.github.lechandonga.clinicalearlywarningjava.tests;

import com.github.lechandonga.clinicalearlywarningjava.alert.AlertTransitionException;
import com.github.lechandonga.clinicalearlywarningjava.domain.Alert;
import com.github.lechandonga.clinicalearlywarningjava.domain.AlertEvent;
import com.github.lechandonga.clinicalearlywarningjava.domain.AlertStatus;
import com.github.lechandonga.clinicalearlywarningjava.domain.RiskLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 业务场景：告警产生、确认、升级、解除的闭环流转，非法流转被拒绝，
 * 已确认后风险继续升高需再次提醒。
 */
@DisplayName("告警状态流转场景")
class AlertLifecycleTest {

    private static final Logger log = LoggerFactory.getLogger(AlertLifecycleTest.class);

    private final TestSupport.Stack stack = new TestSupport.Stack();

    private Alert triggerAlert(String patientId) {
        stack.observationService.ingest(TestSupport.obs("r-hr-" + patientId, patientId, "HR", 150));
        return stack.alertService.byPatient(patientId).get(0);
    }

    @Test
    @DisplayName("完整闭环：产生 -> 确认 -> 升级 -> 解除")
    void fullLifecycle() {
        stack.registerV1();
        Alert alert = triggerAlert("P1");
        assertEquals(AlertStatus.OPEN, alert.getStatus());

        stack.alertService.acknowledge(alert.getAlertId());
        assertEquals(AlertStatus.ACKNOWLEDGED, alert.getStatus());
        stack.alertService.escalate(alert.getAlertId());
        assertEquals(AlertStatus.ESCALATED, alert.getStatus());
        stack.alertService.resolve(alert.getAlertId());
        assertEquals(AlertStatus.RESOLVED, alert.getStatus());

        log.info("用例[完整闭环] 事件轨迹={}", alert.getEvents().stream()
                .map(e -> e.type() + ":" + e.fromStatus() + "->" + e.toStatus()).toList());
        assertEquals(4, alert.getEvents().size());
    }

    @Test
    @DisplayName("非法流转被拒绝并说明原因：解除后不可再确认，确认后不可回到未确认")
    void illegalTransitionsRejected() {
        stack.registerV1();
        Alert alert = triggerAlert("P1");
        stack.alertService.resolve(alert.getAlertId());

        AlertTransitionException e1 = assertThrows(AlertTransitionException.class,
                () -> stack.alertService.acknowledge(alert.getAlertId()));
        log.info("用例[非法流转-终态] 拒绝原因={}", e1.getMessage());
        assertEquals(AlertStatus.RESOLVED, e1.getCurrent());

        Alert alert2 = triggerAlert("P2");
        stack.alertService.acknowledge(alert2.getAlertId());
        AlertTransitionException e2 = assertThrows(AlertTransitionException.class,
                () -> stack.alertService.acknowledge(alert2.getAlertId()));
        log.info("用例[非法流转-重复确认] 拒绝原因={}", e2.getMessage());
        assertEquals(AlertStatus.ACKNOWLEDGED, e2.getCurrent());
        assertEquals(AlertStatus.ACKNOWLEDGED, e2.getTarget());
    }

    @Test
    @DisplayName("已确认患者风险继续升高时再次提醒，且不产生新告警")
    void reNotifyWhenRiskIncreasesAfterAck() {
        stack.registerV1();
        Alert alert = triggerAlert("P1");
        stack.alertService.acknowledge(alert.getAlertId());
        assertEquals(RiskLevel.MEDIUM, alert.getRiskLevel());

        // 风险升高：HR 高 + SBP 低 => 4 分 => HIGH
        stack.observationService.ingest(TestSupport.obs("r-sbp", "P1", "SBP", 80));
        log.info("用例[确认后再提醒] 当前等级={} 状态={} 事件={}",
                alert.getRiskLevel(), alert.getStatus(),
                alert.getEvents().stream().map(AlertEvent::type).toList());
        assertEquals(RiskLevel.HIGH, alert.getRiskLevel());
        assertTrue(alert.getEvents().stream().anyMatch(e -> e.type() == AlertEvent.Type.RE_NOTIFIED));
        assertEquals(1, stack.alertService.byPatient("P1").size());
    }

    @Test
    @DisplayName("风险回落到阈值以下自动解除告警")
    void autoResolveWhenRiskDrops() {
        stack.registerV1();
        Alert alert = triggerAlert("P1");
        // 更新的正常心率覆盖异常值（occurredAt 更晚）
        stack.observationService.ingest(TestSupport.obs("r-normal", "P1", "HR", 75,
                TestSupport.NOW.plusSeconds(60)));
        log.info("用例[风险回落自动解除] 状态={} 事件={}",
                alert.getStatus(), alert.getEvents().stream().map(AlertEvent::type).toList());
        assertEquals(AlertStatus.RESOLVED, alert.getStatus());
    }
}

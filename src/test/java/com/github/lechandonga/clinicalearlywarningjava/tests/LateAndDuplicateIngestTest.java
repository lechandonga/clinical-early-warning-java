package com.github.lechandonga.clinicalearlywarningjava.tests;

import com.github.lechandonga.clinicalearlywarningjava.domain.RiskLevel;
import com.github.lechandonga.clinicalearlywarningjava.ingest.IngestResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 业务场景：迟到数据必须纳入判定，重复上报幂等去重。
 */
@DisplayName("迟到与重复上报场景")
class LateAndDuplicateIngestTest {

    private static final Logger log = LoggerFactory.getLogger(LateAndDuplicateIngestTest.class);

    private final TestSupport.Stack stack = new TestSupport.Stack();

    @Test
    @DisplayName("乱序迟到的异常数据到达后重新判定并升级风险")
    void lateDataIncludedInEvaluation() {
        stack.registerV1();
        IngestResult first = stack.observationService.ingest(
                TestSupport.obs("r1", "P1", "HR", 80, TestSupport.NOW.minusSeconds(600)));
        assertEquals(RiskLevel.LOW, first.evaluation().level());

        // 测量时间更晚但迟到到达的异常心率：必须被纳入判定而不是丢弃
        IngestResult late = stack.observationService.ingest(
                TestSupport.obs("r2", "P1", "HR", 150, TestSupport.NOW.minusSeconds(300)));
        log.info("用例[迟到数据纳入判定] 首次等级={} 迟到数据到达后等级={} 依据={}",
                first.evaluation().level(), late.evaluation().level(), late.evaluation().explain());
        assertEquals(IngestResult.Status.ACCEPTED, late.status());
        assertEquals(RiskLevel.MEDIUM, late.evaluation().level());
        assertEquals(2, stack.observationService.observations("P1").size());
    }

    @Test
    @DisplayName("同一 recordId 重复上报幂等忽略，不影响判定")
    void duplicateIngestIsIdempotent() {
        stack.registerV1();
        IngestResult first = stack.observationService.ingest(TestSupport.obs("r1", "P1", "HR", 150));
        IngestResult dup = stack.observationService.ingest(TestSupport.obs("r1", "P1", "HR", 150));
        log.info("用例[重复上报幂等] 首次={} 重复={} 库存观测数={}",
                first.status(), dup.status(), stack.observationService.observations("P1").size());
        assertEquals(IngestResult.Status.ACCEPTED, first.status());
        assertEquals(IngestResult.Status.DUPLICATE, dup.status());
        assertEquals(1, stack.observationService.observations("P1").size());
    }

    @Test
    @DisplayName("重复触发同一风险不产生重复告警")
    void repeatedTriggerDoesNotDuplicateAlert() {
        stack.registerV1();
        stack.observationService.ingest(TestSupport.obs("r1", "P1", "HR", 150));
        stack.observationService.ingest(TestSupport.obs("r2", "P1", "HR", 160));
        stack.observationService.ingest(TestSupport.obs("r3", "P1", "HR", 155));
        long alertCount = stack.alertService.byPatient("P1").size();
        log.info("用例[重复触发不重复告警] 三次触发后告警数={}", alertCount);
        assertEquals(1, alertCount);
        assertTrue(stack.alertService.byPatient("P1").get(0).getEvents().stream()
                .anyMatch(e -> e.type().name().equals("CREATED")));
    }
}

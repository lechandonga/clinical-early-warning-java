package com.github.lechandonga.clinicalearlywarningjava.tests;

import com.github.lechandonga.clinicalearlywarningjava.domain.Observation;
import com.github.lechandonga.clinicalearlywarningjava.domain.ObservationType;
import com.github.lechandonga.clinicalearlywarningjava.domain.RejectionReason;
import com.github.lechandonga.clinicalearlywarningjava.ingest.IngestResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 业务场景：数据校验失败必须给出可区分的拒绝原因，不得静默接受。
 */
@DisplayName("数据校验失败场景")
class ObservationValidationTest {

    private static final Logger log = LoggerFactory.getLogger(ObservationValidationTest.class);

    private final TestSupport.Stack stack = new TestSupport.Stack();

    @Test
    @DisplayName("关键字段缺失 -> MISSING_FIELD")
    void missingField() {
        stack.registerV1();
        Observation bad = new Observation(null, "P1", ObservationType.VITAL_SIGN,
                "HR", 80.0, "", TestSupport.NOW, null);
        IngestResult result = stack.observationService.ingest(bad);
        log.info("用例[关键字段缺失] 结果={} 原因={} 说明={}", result.status(), result.rejectionReason(), result.message());
        assertEquals(IngestResult.Status.REJECTED, result.status());
        assertEquals(RejectionReason.MISSING_FIELD, result.rejectionReason());
    }

    @Test
    @DisplayName("取值超出配置范围 -> VALUE_OUT_OF_RANGE")
    void outOfRange() {
        stack.registerV1();
        IngestResult result = stack.observationService.ingest(
                TestSupport.obs("r1", "P1", "HR", 999));
        log.info("用例[超范围取值] 结果={} 原因={} 说明={}", result.status(), result.rejectionReason(), result.message());
        assertEquals(IngestResult.Status.REJECTED, result.status());
        assertEquals(RejectionReason.VALUE_OUT_OF_RANGE, result.rejectionReason());
    }

    @Test
    @DisplayName("未配置的观测编码 -> UNKNOWN_CODE")
    void unknownCode() {
        stack.registerV1();
        IngestResult result = stack.observationService.ingest(
                TestSupport.obs("r2", "P1", "UNKNOWN", 1));
        log.info("用例[未知编码] 结果={} 原因={} 说明={}", result.status(), result.rejectionReason(), result.message());
        assertEquals(RejectionReason.UNKNOWN_CODE, result.rejectionReason());
    }

    @Test
    @DisplayName("时间戳超前 -> TIMESTAMP_IN_FUTURE")
    void futureTimestamp() {
        stack.registerV1();
        IngestResult result = stack.observationService.ingest(
                TestSupport.obs("r3", "P1", "HR", 80, TestSupport.NOW.plusSeconds(3600)));
        log.info("用例[时间戳超前] 结果={} 原因={} 说明={}", result.status(), result.rejectionReason(), result.message());
        assertEquals(RejectionReason.TIMESTAMP_IN_FUTURE, result.rejectionReason());
    }

    @Test
    @DisplayName("时间戳过旧 -> TIMESTAMP_TOO_OLD")
    void tooOldTimestamp() {
        stack.registerV1();
        IngestResult result = stack.observationService.ingest(
                TestSupport.obs("r4", "P1", "HR", 80, TestSupport.NOW.minusSeconds(40L * 24 * 3600)));
        log.info("用例[时间戳过旧] 结果={} 原因={} 说明={}", result.status(), result.rejectionReason(), result.message());
        assertEquals(RejectionReason.TIMESTAMP_TOO_OLD, result.rejectionReason());
    }
}

package com.github.lechandonga.clinicalearlywarningjava.tests;

import com.github.lechandonga.clinicalearlywarningjava.exception.ValidationException;
import com.github.lechandonga.clinicalearlywarningjava.service.ObservationService;
import com.github.lechandonga.clinicalearlywarningjava.tests.support.TestClockConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 业务：数据校验。用例覆盖关键字段缺失、未知指标、取值越界、时间戳异常、重复 recordId，
 * 每类失败必须给出可区分的错误码，而不是被静默接受。
 */
@SpringBootTest
@Import(TestClockConfig.class)
class ObservationValidationTest {

    private static final Logger log = LoggerFactory.getLogger(ObservationValidationTest.class);

    @Autowired
    private ObservationService observationService;

    private final Instant now = Instant.parse("2026-10-07T08:00:00Z");

    @BeforeEach
    void resetClock() {
        TestClockConfig.CLOCK.setInstant(now);
    }

    @Nested
    @DisplayName("业务: 上报数据校验（拒绝原因必须可区分）")
    class ValidationCases {

        @Test
        @DisplayName("用例1: 关键字段缺失 -> MISSING_FIELD，逐字段区分")
        void missingFields() {
            assertMissing(null, "P1", "TEMP_C", 37.0, now, "recordId");
            assertMissing("R-1", null, "TEMP_C", 37.0, now, "patientId");
            assertMissing("R-1", "P1", " ", 37.0, now, "metricCode");
            assertMissing("R-1", "P1", "TEMP_C", null, now, "value");
            assertMissing("R-1", "P1", "TEMP_C", 37.0, null, "observedAt");
            log.info("[数据校验] 用例1 关键字段缺失 => 测试通过: recordId/patientId/metricCode/value/observedAt 均返回 MISSING_FIELD");
        }

        @Test
        @DisplayName("用例2: 未知指标 -> UNKNOWN_METRIC")
        void unknownMetric() {
            ValidationException ex = assertThrows(ValidationException.class, () ->
                    observationService.ingest("R-2", "P1", "NOT_EXIST", 1.0, now));
            assertEquals("UNKNOWN_METRIC", ex.getCode());
            assertTrue(ex.getMessage().contains("NOT_EXIST"));
            log.info("[数据校验] 用例2 未知指标 => 测试通过: code={}, msg={}", ex.getCode(), ex.getMessage());
        }

        @Test
        @DisplayName("用例3: 取值超出配置范围 -> VALUE_OUT_OF_RANGE（过高与过低）")
        void outOfRange() {
            ValidationException high = assertThrows(ValidationException.class, () ->
                    observationService.ingest("R-3", "P1", "TEMP_C", 46.0, now));
            ValidationException low = assertThrows(ValidationException.class, () ->
                    observationService.ingest("R-4", "P1", "O2_SAT", 40.0, now));
            assertEquals("VALUE_OUT_OF_RANGE", high.getCode());
            assertEquals("VALUE_OUT_OF_RANGE", low.getCode());
            log.info("[数据校验] 用例3 取值越界 => 测试通过: 过高[{}] 过低[{}]", high.getMessage(), low.getMessage());
        }

        @Test
        @DisplayName("用例4: 时间戳异常 -> INVALID_TIMESTAMP（未来太远 / 历史太久）")
        void invalidTimestamp() {
            ValidationException future = assertThrows(ValidationException.class, () ->
                    observationService.ingest("R-5", "P1", "TEMP_C", 37.0, now.plusSeconds(3600)));
            ValidationException old = assertThrows(ValidationException.class, () ->
                    observationService.ingest("R-6", "P1", "TEMP_C", 37.0, now.minusSeconds(31L * 86400)));
            assertEquals("INVALID_TIMESTAMP", future.getCode());
            assertEquals("INVALID_TIMESTAMP", old.getCode());
            log.info("[数据校验] 用例4 时间戳异常 => 测试通过: 未来[{}] 过旧[{}]", future.getMessage(), old.getMessage());
        }

        @Test
        @DisplayName("用例5: 相同 recordId 重复上报 -> DUPLICATE_RECORD")
        void duplicateRecordId() {
            observationService.ingest("R-DUP", "P1", "TEMP_C", 36.8, now);
            ValidationException ex = assertThrows(ValidationException.class, () ->
                    observationService.ingest("R-DUP", "P1", "HEART_RATE", 80.0, now.plusSeconds(10)));
            assertEquals("DUPLICATE_RECORD", ex.getCode());
            log.info("[数据校验] 用例5 重复 recordId => 测试通过: code={}, msg={}", ex.getCode(), ex.getMessage());
        }

        private void assertMissing(String recordId, String patientId, String metricCode,
                                  Double value, Instant observedAt, String field) {
            ValidationException ex = assertThrows(ValidationException.class, () ->
                    observationService.ingest(recordId, patientId, metricCode, value, observedAt));
            assertEquals("MISSING_FIELD", ex.getCode(), "字段 " + field + " 应被拒绝");
            assertTrue(ex.getMessage().contains(field), "拒绝原因应指出字段 " + field);
        }
    }
}

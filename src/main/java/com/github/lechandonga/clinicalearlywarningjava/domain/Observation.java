package com.github.lechandonga.clinicalearlywarningjava.domain;

import java.time.Instant;

/**
 * 一条患者体征或检验结果上报记录。
 *
 * @param recordId   上报方提供的唯一记录标识，用于幂等去重
 * @param patientId  患者标识
 * @param type       体征 / 检验
 * @param code       观测项目编码，如 HR、SBP、SPO2、LACTATE
 * @param value      数值
 * @param unit       单位（仅记录，不参与判定）
 * @param occurredAt 观测发生时间（允许乱序、迟到）
 * @param receivedAt 服务端接收时间
 */
public record Observation(
        String recordId,
        String patientId,
        ObservationType type,
        String code,
        Double value,
        String unit,
        Instant occurredAt,
        Instant receivedAt) {
}

package com.github.lechandonga.clinicalearlywarningjava.ingest;

import com.github.lechandonga.clinicalearlywarningjava.domain.RejectionReason;
import com.github.lechandonga.clinicalearlywarningjava.rule.RiskEvaluation;

/**
 * 上报处理结果：接受（含本次风险判定结论）、幂等重复、或带原因的拒绝。
 */
public record IngestResult(
        Status status,
        RejectionReason rejectionReason,
        String message,
        RiskEvaluation evaluation) {

    public enum Status {
        ACCEPTED,
        DUPLICATE,
        REJECTED
    }

    public static IngestResult accepted(RiskEvaluation evaluation) {
        return new IngestResult(Status.ACCEPTED, null, "已接受并完成风险判定", evaluation);
    }

    public static IngestResult duplicate(String recordId) {
        return new IngestResult(Status.DUPLICATE, null, "重复上报，已幂等忽略: " + recordId, null);
    }

    public static IngestResult rejected(RejectionReason reason, String message) {
        return new IngestResult(Status.REJECTED, reason, message, null);
    }
}

package com.github.lechandonga.clinicalearlywarningjava.exception;

/**
 * 上报数据校验失败基类，携带可区分的错误码，便于调用方区分拒绝原因。
 */
public class ValidationException extends RuntimeException {
    private final String code;

    public ValidationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static ValidationException missingField(String field) {
        return new ValidationException("MISSING_FIELD", "字段缺失: " + field);
    }

    public static ValidationException outOfRange(String metricCode, double value, double min, double max) {
        return new ValidationException("VALUE_OUT_OF_RANGE",
                "指标 " + metricCode + " 取值 " + value + " 超出配置范围 [" + min + ", " + max + "]");
    }

    public static ValidationException unknownMetric(String metricCode) {
        return new ValidationException("UNKNOWN_METRIC", "未配置的指标代码: " + metricCode);
    }

    public static ValidationException timestampInvalid(String detail) {
        return new ValidationException("INVALID_TIMESTAMP", "时间戳异常: " + detail);
    }

    public static ValidationException duplicateRecord(String recordId) {
        return new ValidationException("DUPLICATE_RECORD", "重复上报: recordId=" + recordId + " 已接收");
    }
}

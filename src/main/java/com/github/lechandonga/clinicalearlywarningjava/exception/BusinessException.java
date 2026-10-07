package com.github.lechandonga.clinicalearlywarningjava.exception;

/**
 * 业务规则冲突，如非法的告警状态流转、规则版本冲突等。
 */
public class BusinessException extends RuntimeException {
    private final String code;

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}

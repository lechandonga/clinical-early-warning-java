package com.github.lechandonga.clinicalearlywarningjava.alert;

public class AlertNotFoundException extends RuntimeException {
    public AlertNotFoundException(String alertId) {
        super("告警不存在: " + alertId);
    }
}

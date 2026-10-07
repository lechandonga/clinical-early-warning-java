package com.github.lechandonga.clinicalearlywarningjava.web.dto;

import com.github.lechandonga.clinicalearlywarningjava.domain.AlertAction;

public class AlertTransitionRequest {
    private AlertAction action;
    private String operator;
    private String reason;

    public AlertAction getAction() { return action; }
    public void setAction(AlertAction action) { this.action = action; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}

package com.github.lechandonga.clinicalearlywarningjava.web;

import com.github.lechandonga.clinicalearlywarningjava.domain.Alert;

import java.util.LinkedHashMap;
import java.util.Map;

final class AlertViews {

    private AlertViews() {
    }

    static Map<String, Object> of(Alert alert) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", alert.getId());
        view.put("patientId", alert.getPatientId());
        view.put("ruleVersionId", alert.getRuleVersionId());
        view.put("status", alert.getStatus());
        view.put("severity", alert.getSeverity());
        view.put("initialScore", alert.getInitialScore());
        view.put("lastScore", alert.getLastScore());
        view.put("escalationLevel", alert.getEscalationLevel());
        view.put("createdAt", alert.getCreatedAt());
        view.put("initialEvidence", alert.getInitialEvidence());
        view.put("events", alert.getEvents());
        return view;
    }
}

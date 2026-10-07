package com.github.lechandonga.clinicalearlywarningjava.web.dto;

import java.time.Instant;

public class ObservationRequest {
    private String recordId;
    private String patientId;
    private String metricCode;
    private Double value;
    private Instant observedAt;

    public String getRecordId() { return recordId; }
    public void setRecordId(String recordId) { this.recordId = recordId; }
    public String getPatientId() { return patientId; }
    public void setPatientId(String patientId) { this.patientId = patientId; }
    public String getMetricCode() { return metricCode; }
    public void setMetricCode(String metricCode) { this.metricCode = metricCode; }
    public Double getValue() { return value; }
    public void setValue(Double value) { this.value = value; }
    public Instant getObservedAt() { return observedAt; }
    public void setObservedAt(Instant observedAt) { this.observedAt = observedAt; }
}

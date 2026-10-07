package com.github.lechandonga.clinicalearlywarningjava.store;

import com.github.lechandonga.clinicalearlywarningjava.domain.Alert;
import com.github.lechandonga.clinicalearlywarningjava.domain.Assessment;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 告警存储：每个患者至多一条未关闭告警；评估历史只追加不改写。
 */
@Component
public class AlertStore {

    private final ConcurrentHashMap<String, Alert> openAlerts = new ConcurrentHashMap<>();
    private final List<Alert> allAlerts = new CopyOnWriteArrayList<>();
    private final List<Assessment> assessments = new CopyOnWriteArrayList<>();

    public Optional<Alert> findOpen(String patientId) {
        return Optional.ofNullable(openAlerts.get(patientId));
    }

    public Alert create(Alert alert) {
        allAlerts.add(alert);
        openAlerts.put(alert.getPatientId(), alert);
        return alert;
    }

    public void markResolved(Alert alert) {
        openAlerts.remove(alert.getPatientId(), alert);
    }

    public Optional<Alert> findById(String alertId) {
        return allAlerts.stream().filter(a -> a.getId().equals(alertId)).findFirst();
    }

    public List<Alert> listByPatient(String patientId) {
        return allAlerts.stream()
                .filter(a -> a.getPatientId().equals(patientId))
                .sorted(Comparator.comparing(Alert::getCreatedAt))
                .toList();
    }

    public List<Alert> listOpen() {
        return List.copyOf(openAlerts.values());
    }

    public void appendAssessment(Assessment assessment) {
        assessments.add(assessment);
    }

    public List<Assessment> listAssessments(String patientId) {
        return assessments.stream()
                .filter(a -> a.patientId().equals(patientId))
                .sorted(Comparator.comparing(Assessment::evaluatedAt))
                .toList();
    }
}

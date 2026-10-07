package com.github.lechandonga.clinicalearlywarningjava.api;

import com.github.lechandonga.clinicalearlywarningjava.alert.AlertNotFoundException;
import com.github.lechandonga.clinicalearlywarningjava.alert.AlertService;
import com.github.lechandonga.clinicalearlywarningjava.alert.AlertTransitionException;
import com.github.lechandonga.clinicalearlywarningjava.domain.Alert;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    public List<Alert> all() {
        return alertService.all();
    }

    @GetMapping("/patient/{patientId}")
    public List<Alert> byPatient(@PathVariable String patientId) {
        return alertService.byPatient(patientId);
    }

    @PostMapping("/{alertId}/acknowledge")
    public Alert acknowledge(@PathVariable String alertId) {
        return alertService.acknowledge(alertId);
    }

    @PostMapping("/{alertId}/escalate")
    public Alert escalate(@PathVariable String alertId) {
        return alertService.escalate(alertId);
    }

    @PostMapping("/{alertId}/resolve")
    public Alert resolve(@PathVariable String alertId) {
        return alertService.resolve(alertId);
    }

    @ExceptionHandler(AlertNotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(AlertNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(AlertTransitionException.class)
    public ResponseEntity<Map<String, String>> illegalTransition(AlertTransitionException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", e.getMessage(),
                "current", e.getCurrent().name(),
                "target", e.getTarget().name()));
    }
}

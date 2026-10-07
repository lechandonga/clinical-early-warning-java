package com.github.lechandonga.clinicalearlywarningjava.api;

import com.github.lechandonga.clinicalearlywarningjava.domain.Observation;
import com.github.lechandonga.clinicalearlywarningjava.ingest.IngestResult;
import com.github.lechandonga.clinicalearlywarningjava.ingest.ObservationService;
import com.github.lechandonga.clinicalearlywarningjava.rule.RiskEvaluation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ObservationController {

    private final ObservationService observationService;

    public ObservationController(ObservationService observationService) {
        this.observationService = observationService;
    }

    @PostMapping("/observations")
    public ResponseEntity<IngestResult> ingest(@RequestBody Observation observation) {
        IngestResult result = observationService.ingest(observation);
        return switch (result.status()) {
            case ACCEPTED -> ResponseEntity.ok(result);
            case DUPLICATE -> ResponseEntity.status(HttpStatus.CONFLICT).body(result);
            case REJECTED -> ResponseEntity.unprocessableEntity().body(result);
        };
    }

    @GetMapping("/patients/{patientId}/observations")
    public List<Observation> observations(@PathVariable String patientId) {
        return observationService.observations(patientId);
    }

    @GetMapping("/patients/{patientId}/evaluations")
    public List<RiskEvaluation> evaluations(@PathVariable String patientId) {
        return observationService.evaluationLog(patientId);
    }

    @PostMapping("/patients/{patientId}/replay")
    public RiskEvaluation replay(@PathVariable String patientId, @RequestParam String ruleVersion) {
        return observationService.replay(patientId, ruleVersion);
    }
}

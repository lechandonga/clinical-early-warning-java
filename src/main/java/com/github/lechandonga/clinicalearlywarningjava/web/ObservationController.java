package com.github.lechandonga.clinicalearlywarningjava.web;

import com.github.lechandonga.clinicalearlywarningjava.domain.Observation;
import com.github.lechandonga.clinicalearlywarningjava.domain.MetricRegistry;
import com.github.lechandonga.clinicalearlywarningjava.service.ObservationService;
import com.github.lechandonga.clinicalearlywarningjava.service.ReplayService;
import com.github.lechandonga.clinicalearlywarningjava.store.AlertStore;
import com.github.lechandonga.clinicalearlywarningjava.store.ObservationStore;
import com.github.lechandonga.clinicalearlywarningjava.web.dto.ObservationRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ObservationController {

    private final ObservationService observationService;
    private final ObservationStore observationStore;
    private final AlertStore alertStore;
    private final ReplayService replayService;
    private final MetricRegistry metricRegistry;

    public ObservationController(ObservationService observationService, ObservationStore observationStore,
                                 AlertStore alertStore, ReplayService replayService,
                                 MetricRegistry metricRegistry) {
        this.observationService = observationService;
        this.observationStore = observationStore;
        this.alertStore = alertStore;
        this.replayService = replayService;
        this.metricRegistry = metricRegistry;
    }

    /** 持续上报单条体征 / 检验结果。 */
    @PostMapping("/observations")
    public ObservationService.IngestResult ingest(@RequestBody ObservationRequest request) {
        return observationService.ingest(request.getRecordId(), request.getPatientId(),
                request.getMetricCode(), request.getValue(), request.getObservedAt());
    }

    /** 查询患者已接收的全部观测（按观测时间排序，迟到数据已并入）。 */
    @GetMapping("/patients/{patientId}/observations")
    public List<Observation> observations(@PathVariable String patientId) {
        return observationStore.listByPatient(patientId);
    }

    /** 用指定 / 当时生效的规则版本重放患者截至某时刻的数据，只读、不改告警。 */
    @GetMapping("/patients/{patientId}/replay")
    public Object replay(@PathVariable String patientId,
                         @RequestParam Instant asOf,
                         @RequestParam(required = false) String ruleVersionId) {
        return replayService.replayAt(patientId, asOf, ruleVersionId);
    }

    /** 患者评估历史（每次上报追加一条，规则更新不改写历史）。 */
    @GetMapping("/patients/{patientId}/assessments")
    public Object assessments(@PathVariable String patientId) {
        return alertStore.listAssessments(patientId);
    }

    /** 患者告警及完整流转轨迹。 */
    @GetMapping("/patients/{patientId}/alerts")
    public Object alerts(@PathVariable String patientId) {
        return alertStore.listByPatient(patientId).stream()
                .map(AlertViews::of)
                .toList();
    }

    /** 当前所有未关闭告警。 */
    @GetMapping("/alerts/open")
    public Object openAlerts() {
        return alertStore.listOpen().stream().map(AlertViews::of).toList();
    }

    @GetMapping("/metrics")
    public Object metrics() {
        return metricRegistry.all().values();
    }
}

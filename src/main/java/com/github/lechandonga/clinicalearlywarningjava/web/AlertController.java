package com.github.lechandonga.clinicalearlywarningjava.web;

import com.github.lechandonga.clinicalearlywarningjava.domain.AlertAction;
import com.github.lechandonga.clinicalearlywarningjava.exception.BusinessException;
import com.github.lechandonga.clinicalearlywarningjava.service.AlertService;
import com.github.lechandonga.clinicalearlywarningjava.web.dto.AlertTransitionRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    /** 告警流转：ACKNOWLEDGE / ESCALATE / RESOLVE，非法流转返回 409 与原因。 */
    @PostMapping("/{alertId}/transition")
    public Map<String, Object> transition(@PathVariable String alertId,
                                          @RequestBody AlertTransitionRequest request) {
        if (request.getAction() == null) {
            throw new BusinessException("MISSING_FIELD", "字段缺失: action");
        }
        return AlertViews.of(alertService.transition(alertId, request.getAction(),
                request.getOperator(), request.getReason()));
    }

    @GetMapping("/{alertId}")
    public Map<String, Object> get(@PathVariable String alertId) {
        return AlertViews.of(alertService.get(alertId));
    }
}

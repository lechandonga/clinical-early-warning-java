package com.github.lechandonga.clinicalearlywarningjava.web;

import com.github.lechandonga.clinicalearlywarningjava.domain.RuleCondition;
import com.github.lechandonga.clinicalearlywarningjava.domain.RuleVersion;
import com.github.lechandonga.clinicalearlywarningjava.service.RuleService;
import com.github.lechandonga.clinicalearlywarningjava.web.dto.RulePublishRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/rules")
public class RuleController {

    private final RuleService ruleService;

    public RuleController(RuleService ruleService) {
        this.ruleService = ruleService;
    }

    /** 发布新规则版本（版本不可变，按 effectiveFrom 生效，不影响历史判定）。 */
    @PostMapping("/versions")
    public RuleVersion publish(@RequestBody RulePublishRequest request) {
        List<RuleCondition> conditions = request.getConditions() == null ? List.of()
                : request.getConditions().stream()
                .map(dto -> new RuleCondition(dto.getMetricCode(), dto.getOperator(),
                        dto.getThreshold(), dto.getScore()))
                .toList();
        return ruleService.publish(request.getVersionId(), request.getDescription(),
                request.getEffectiveFrom(), conditions, request.getMediumScore(), request.getHighScore());
    }

    @GetMapping("/versions")
    public List<RuleVersion> list() {
        return ruleService.listVersions();
    }
}

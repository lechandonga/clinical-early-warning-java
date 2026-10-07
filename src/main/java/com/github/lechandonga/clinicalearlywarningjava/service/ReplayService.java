package com.github.lechandonga.clinicalearlywarningjava.service;

import com.github.lechandonga.clinicalearlywarningjava.domain.Assessment;
import com.github.lechandonga.clinicalearlywarningjava.domain.Observation;
import com.github.lechandonga.clinicalearlywarningjava.domain.RuleVersion;
import com.github.lechandonga.clinicalearlywarningjava.exception.BusinessException;
import com.github.lechandonga.clinicalearlywarningjava.store.ObservationStore;
import com.github.lechandonga.clinicalearlywarningjava.store.RuleStore;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * 只读重放：用指定（或观测时刻生效）的规则版本，对患者截至某时刻的数据重新判定。
 * 重放不写任何状态、不改告警，只产出评估结论，用于验证版本一致性与历史可解释性。
 */
@Service
public class ReplayService {

    private final ObservationStore observationStore;
    private final RuleStore ruleStore;
    private final RuleEngine ruleEngine;

    public ReplayService(ObservationStore observationStore, RuleStore ruleStore, RuleEngine ruleEngine) {
        this.observationStore = observationStore;
        this.ruleStore = ruleStore;
        this.ruleEngine = ruleEngine;
    }

    public Assessment replayAt(String patientId, Instant asOf, String ruleVersionId) {
        RuleVersion rules;
        if (ruleVersionId != null && !ruleVersionId.isBlank()) {
            rules = ruleStore.get(ruleVersionId)
                    .orElseThrow(() -> new BusinessException("RULE_VERSION_NOT_FOUND",
                            "规则版本不存在: " + ruleVersionId));
        } else {
            rules = ruleStore.effectiveAt(asOf)
                    .orElseThrow(() -> new BusinessException("NO_RULE_VERSION", "当前没有可用的风险规则版本"));
        }
        List<Observation> data = observationStore.snapshotAt(patientId, asOf);
        return ruleEngine.evaluate(patientId, asOf, rules, data);
    }
}

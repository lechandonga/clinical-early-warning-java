package com.github.lechandonga.clinicalearlywarningjava.service;

import com.github.lechandonga.clinicalearlywarningjava.domain.RuleCondition;
import com.github.lechandonga.clinicalearlywarningjava.domain.RuleVersion;
import com.github.lechandonga.clinicalearlywarningjava.exception.BusinessException;
import com.github.lechandonga.clinicalearlywarningjava.store.RuleStore;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * 规则版本管理：发布（只增不改）、查询。新版本可指定生效时间；默认自发布时刻生效，
 * 不影响此前数据的历史判定。
 */
@Service
public class RuleService {

    private final RuleStore ruleStore;
    private final Clock clock;

    public RuleService(RuleStore ruleStore, Clock clock) {
        this.ruleStore = ruleStore;
        this.clock = clock;
    }

    public RuleVersion publish(String versionId, String description, Instant effectiveFrom,
                               List<RuleCondition> conditions, int mediumScore, int highScore) {
        if (versionId == null || versionId.isBlank()) {
            throw new BusinessException("INVALID_RULE", "规则版本号不能为空");
        }
        if (conditions == null || conditions.isEmpty()) {
            throw new BusinessException("INVALID_RULE", "规则至少包含一条条件");
        }
        if (mediumScore <= 0 || highScore <= mediumScore) {
            throw new BusinessException("INVALID_RULE",
                    "阈值需满足 0 < mediumScore < highScore，当前 medium=" + mediumScore + ", high=" + highScore);
        }
        Instant from = effectiveFrom != null ? effectiveFrom : clock.instant();
        if (ruleStore.get(versionId).isPresent()) {
            throw new BusinessException("RULE_VERSION_EXISTS", "规则版本号已存在: " + versionId);
        }
        RuleVersion version = new RuleVersion(versionId, description, clock.instant(),
                from, List.copyOf(conditions), mediumScore, highScore);
        ruleStore.add(version);
        return version;
    }

    public List<RuleVersion> listVersions() {
        return ruleStore.all();
    }

    public RuleVersion get(String versionId) {
        return ruleStore.get(versionId)
                .orElseThrow(() -> new BusinessException("RULE_VERSION_NOT_FOUND", "规则版本不存在: " + versionId));
    }
}

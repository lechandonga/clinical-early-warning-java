package com.github.lechandonga.clinicalearlywarningjava.store;

import com.github.lechandonga.clinicalearlywarningjava.domain.RuleVersion;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 规则版本存储。版本只增不改；按 effectiveFrom 选择“观测发生时生效”的版本。
 */
@Component
public class RuleStore {

    private final List<RuleVersion> versions = new CopyOnWriteArrayList<>();

    public void add(RuleVersion version) {
        boolean idExists = versions.stream().anyMatch(v -> v.versionId().equals(version.versionId()));
        if (idExists) {
            throw new IllegalStateException("规则版本已存在: " + version.versionId());
        }
        versions.add(version);
    }

    public List<RuleVersion> all() {
        return versions.stream()
                .sorted(Comparator.comparing(RuleVersion::effectiveFrom))
                .toList();
    }

    public Optional<RuleVersion> get(String versionId) {
        return versions.stream().filter(v -> v.versionId().equals(versionId)).findFirst();
    }

    /** 观测时间点 t 当时生效的版本：effectiveFrom <= t 中最新者；否则取最早版本兜底。 */
    public Optional<RuleVersion> effectiveAt(Instant t) {
        List<RuleVersion> sorted = all();
        if (sorted.isEmpty()) {
            return Optional.empty();
        }
        RuleVersion chosen = null;
        for (RuleVersion v : sorted) {
            if (v.effectiveAt(t)) {
                chosen = v;
            } else {
                break;
            }
        }
        return Optional.ofNullable(chosen != null ? chosen : sorted.get(0));
    }

    public Optional<RuleVersion> latest() {
        return all().stream().max(Comparator.comparing(RuleVersion::effectiveFrom));
    }
}

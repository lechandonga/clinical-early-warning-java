package com.github.lechandonga.clinicalearlywarningjava.rule;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 规则版本注册表。所有历史版本永久保留，支持任意版本重放；
 * 激活版本切换只影响新判定，不回写历史。
 */
@Component
public class RuleRegistry {

    private final Map<String, RuleVersion> versions = new ConcurrentHashMap<>();
    private final AtomicReference<RuleVersion> active = new AtomicReference<>();

    public synchronized RuleVersion register(RuleVersion version, boolean activate) {
        if (versions.putIfAbsent(version.version(), version) != null) {
            throw new IllegalArgumentException("规则版本已存在，规则不可变: " + version.version());
        }
        if (activate || active.get() == null) {
            active.set(version);
        }
        return version;
    }

    public RuleVersion activeVersion() {
        RuleVersion v = active.get();
        if (v == null) {
            throw new IllegalStateException("尚未注册任何规则版本");
        }
        return v;
    }

    public synchronized void activate(String version) {
        RuleVersion v = versions.get(version);
        if (v == null) {
            throw new IllegalArgumentException("规则版本不存在: " + version);
        }
        active.set(v);
    }

    public Optional<RuleVersion> find(String version) {
        return Optional.ofNullable(versions.get(version));
    }

    public List<RuleVersion> allVersions() {
        return List.copyOf(versions.values());
    }
}

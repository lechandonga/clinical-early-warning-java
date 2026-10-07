package com.github.lechandonga.clinicalearlywarningjava.api;

import com.github.lechandonga.clinicalearlywarningjava.rule.RuleRegistry;
import com.github.lechandonga.clinicalearlywarningjava.rule.RuleVersion;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rules")
public class RuleController {

    private final RuleRegistry ruleRegistry;

    public RuleController(RuleRegistry ruleRegistry) {
        this.ruleRegistry = ruleRegistry;
    }

    @GetMapping
    public List<RuleVersion> all() {
        return ruleRegistry.allVersions();
    }

    @GetMapping("/active")
    public RuleVersion active() {
        return ruleRegistry.activeVersion();
    }

    @PostMapping
    public RuleVersion register(@RequestBody RuleVersion version,
                                @RequestParam(defaultValue = "false") boolean activate) {
        return ruleRegistry.register(version, activate);
    }

    @PostMapping("/{version}/activate")
    public ResponseEntity<Map<String, String>> activate(@PathVariable String version) {
        ruleRegistry.activate(version);
        return ResponseEntity.ok(Map.of("active", version));
    }
}

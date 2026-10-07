package com.github.lechandonga.clinicalearlywarningjava.domain;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * 指标注册表：定义支持上报的体征 / 检验指标及合法范围，作为入参校验依据。
 */
@Component
public class MetricRegistry {

    private final Map<String, MetricDef> metrics = Map.ofEntries(
            Map.entry("TEMP_C", new MetricDef("TEMP_C", MetricType.VITAL, 30.0, 45.0)),
            Map.entry("HEART_RATE", new MetricDef("HEART_RATE", MetricType.VITAL, 20.0, 250.0)),
            Map.entry("RESP_RATE", new MetricDef("RESP_RATE", MetricType.VITAL, 0.0, 80.0)),
            Map.entry("O2_SAT", new MetricDef("O2_SAT", MetricType.VITAL, 50.0, 100.0)),
            Map.entry("SYS_BP", new MetricDef("SYS_BP", MetricType.VITAL, 30.0, 300.0)),
            Map.entry("DIA_BP", new MetricDef("DIA_BP", MetricType.VITAL, 20.0, 200.0)),
            Map.entry("CONSCIOUS", new MetricDef("CONSCIOUS", MetricType.VITAL, 0.0, 1.0)),
            Map.entry("LACTATE", new MetricDef("LACTATE", MetricType.LAB, 0.2, 25.0)),
            Map.entry("WBC", new MetricDef("WBC", MetricType.LAB, 0.1, 200.0)),
            Map.entry("CREATININE", new MetricDef("CREATININE", MetricType.LAB, 10.0, 2000.0))
    );

    public Optional<MetricDef> find(String code) {
        return Optional.ofNullable(metrics.get(code));
    }

    public Map<String, MetricDef> all() {
        return metrics;
    }
}

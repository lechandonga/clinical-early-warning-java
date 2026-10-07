package com.github.lechandonga.clinicalearlywarningjava.bootstrap;

import com.github.lechandonga.clinicalearlywarningjava.config.ClinicalWarningProperties;
import com.github.lechandonga.clinicalearlywarningjava.domain.MetricDef;
import com.github.lechandonga.clinicalearlywarningjava.domain.MetricRegistry;
import com.github.lechandonga.clinicalearlywarningjava.domain.MetricType;
import com.github.lechandonga.clinicalearlywarningjava.service.ObservationService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

/**
 * 本地数据模拟：定时为若干虚拟患者上报体征 / 检验结果，偶发异常值以触发风险告警。
 * 默认关闭，通过 --clinical-warning.simulation.enabled=true 或 sim profile 开启。
 */
@Component
public class SimulationRunner {

    private static final Logger log = LoggerFactory.getLogger(SimulationRunner.class);

    private final ClinicalWarningProperties properties;
    private final MetricRegistry metricRegistry;
    private final ObservationService observationService;
    private final Clock clock;
    private final AtomicLong sequence = new AtomicLong();
    private ScheduledExecutorService executor;

    public SimulationRunner(ClinicalWarningProperties properties, MetricRegistry metricRegistry,
                            ObservationService observationService, Clock clock) {
        this.properties = properties;
        this.metricRegistry = metricRegistry;
        this.observationService = observationService;
        this.clock = clock;
    }

    @PostConstruct
    public void start() {
        if (!properties.getSimulation().isEnabled()) {
            log.info("本地模拟上报未开启（clinical-warning.simulation.enabled=false）");
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "simulation-reporter");
            t.setDaemon(true);
            return t;
        });
        long intervalMs = properties.getSimulation().getInterval().toMillis();
        executor.scheduleAtFixedRate(this::tickSafely, 2, Math.max(1, intervalMs / 1000), TimeUnit.SECONDS);
        log.info("本地模拟上报已开启：患者数={}，间隔={}s",
                properties.getSimulation().getPatientCount(), intervalMs / 1000);
    }

    private void tickSafely() {
        try {
            tick();
        } catch (Exception ex) {
            log.warn("模拟上报一轮出现（预期内的）拒绝: {}", ex.getMessage());
        }
    }

    private void tick() {
        RandomGenerator random = RandomGeneratorFactory.of("Random").create();
        List<MetricDef> all = metricRegistry.all().values().stream().toList();
        for (int p = 1; p <= properties.getSimulation().getPatientCount(); p++) {
            String patientId = "SIM-P" + p;
            MetricDef metric = all.get(random.nextInt(all.size()));
            double value;
            if (p == 1 && random.nextInt(100) < 35) {
                value = abnormalValue(metric, random);
            } else if (random.nextInt(100) < 5) {
                value = metric.max() + 10;
            } else {
                value = normalValue(metric, random);
            }
            Instant observedAt = clock.instant().minusSeconds(random.nextInt(60));
            String recordId = "SIM-" + sequence.incrementAndGet() + "-" + UUID.randomUUID().toString().substring(0, 8);
            observationService.ingest(recordId, patientId, metric.code(), value, observedAt);
        }
    }

    private double normalValue(MetricDef metric, RandomGenerator random) {
        double low = Math.max(metric.min(), metric.min() + (metric.max() - metric.min()) * 0.25);
        double high = metric.min() + (metric.max() - metric.min()) * 0.6;
        double value = low + random.nextDouble() * (high - low);
        return round(value);
    }

    private double abnormalValue(MetricDef metric, RandomGenerator random) {
        if (metric.type() == MetricType.VITAL) {
            double high = Math.min(metric.max(), metric.min() + (metric.max() - metric.min()) * 0.98);
            double low = Math.max(metric.min(), metric.max() - (metric.max() - metric.min()) * 0.02);
            return round(random.nextBoolean() ? metric.min() + 0.5 : high);
        }
        return round(metric.min() + (metric.max() - metric.min()) * 0.95);
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    @PreDestroy
    public void stop() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }
}

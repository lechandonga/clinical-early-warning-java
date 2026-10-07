package com.github.lechandonga.clinicalearlywarningjava.store;

import com.github.lechandonga.clinicalearlywarningjava.domain.Observation;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 观测内存存储：
 * - recordIds 全局去重；
 * - 同一 (患者, 指标, 观测时间点) 视为同一条数据，重复上报合并而不是新增；
 * - 按观测时间戳有序保存，支持乱序 / 迟到写入后按时间窗快照重放。
 */
@Component
public class ObservationStore {

    public record MetricTime(String metricCode, java.time.Instant observedAt) {
    }

    private final Map<String, Map<MetricTime, Observation>> byPatient = new ConcurrentHashMap<>();
    private final Set<String> recordIds = ConcurrentHashMap.newKeySet();

    public boolean isDuplicateRecord(String recordId) {
        return recordIds.contains(recordId);
    }

    /**
     * @return true 表示新观测被接受；false 表示同 (指标, 时间点) 的数据已存在（幂等合并）。
     */
    public boolean save(Observation obs) {
        recordIds.add(obs.recordId());
        Map<MetricTime, Observation> series =
                byPatient.computeIfAbsent(obs.patientId(), k -> new ConcurrentHashMap<>());
        MetricTime key = new MetricTime(obs.metricCode(), obs.observedAt());
        return series.putIfAbsent(key, obs) == null;
    }

    public List<Observation> listByPatient(String patientId) {
        Map<MetricTime, Observation> series = byPatient.get(patientId);
        if (series == null) {
            return List.of();
        }
        List<Observation> all = new ArrayList<>(series.values());
        all.sort(Comparator.comparing(Observation::observedAt)
                .thenComparing(Observation::metricCode)
                .thenComparing(Observation::recordId));
        return all;
    }

    /** 取患者在某时间点之前（含）的全部观测快照，用于按历史时刻确定性重放。 */
    public List<Observation> snapshotAt(String patientId, java.time.Instant asOf) {
        return listByPatient(patientId).stream()
                .filter(o -> !o.observedAt().isAfter(asOf))
                .toList();
    }

    public NavigableMap<java.time.Instant, List<Observation>> groupedByTime(String patientId) {
        NavigableMap<java.time.Instant, List<Observation>> grouped = new java.util.TreeMap<>();
        for (Observation o : listByPatient(patientId)) {
            grouped.computeIfAbsent(o.observedAt(), k -> new ArrayList<>()).add(o);
        }
        return grouped;
    }
}

package com.github.lechandonga.clinicalearlywarningjava.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 关键配置项（前缀 clinical-warning）：
 * - max-timestamp-future：观测时间允许领先接收时钟的上限，超过视为未来时间戳异常。
 * - max-timestamp-age：观测时间允许晚于接收时钟的历史跨度，超过视为过旧时间戳异常。
 * - lookback：风险判定时纳入的观测窗口（每个指标取窗口内最新值）。
 * - simulation.enabled：是否启动本地模拟上报。
 * - simulation.*：模拟器患者数量与上报间隔。
 */
@ConfigurationProperties(prefix = "clinical-warning")
public class ClinicalWarningProperties {

    private Duration maxTimestampFuture = Duration.ofMinutes(5);
    private Duration maxTimestampAge = Duration.ofDays(30);
    private Duration lookback = Duration.ofHours(24);

    private Simulation simulation = new Simulation();

    public Duration getMaxTimestampFuture() { return maxTimestampFuture; }
    public void setMaxTimestampFuture(Duration maxTimestampFuture) { this.maxTimestampFuture = maxTimestampFuture; }
    public Duration getMaxTimestampAge() { return maxTimestampAge; }
    public void setMaxTimestampAge(Duration maxTimestampAge) { this.maxTimestampAge = maxTimestampAge; }
    public Duration getLookback() { return lookback; }
    public void setLookback(Duration lookback) { this.lookback = lookback; }
    public Simulation getSimulation() { return simulation; }
    public void setSimulation(Simulation simulation) { this.simulation = simulation; }

    public static class Simulation {
        private boolean enabled = false;
        private int patientCount = 3;
        private Duration interval = Duration.ofSeconds(10);

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getPatientCount() { return patientCount; }
        public void setPatientCount(int patientCount) { this.patientCount = patientCount; }
        public Duration getInterval() { return interval; }
        public void setInterval(Duration interval) { this.interval = interval; }
    }
}

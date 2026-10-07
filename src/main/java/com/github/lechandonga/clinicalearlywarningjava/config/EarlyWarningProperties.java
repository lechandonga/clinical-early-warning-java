package com.github.lechandonga.clinicalearlywarningjava.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 关键配置项（前缀 early-warning）：
 * - ranges: 每个观测项目编码的合法取值范围
 * - max-future-skew: 允许的时间戳超前上限，超出拒绝
 * - max-past-age: 允许的历史数据最大年龄，超出拒绝（之内的迟到数据正常纳入判定）
 */
@ConfigurationProperties(prefix = "early-warning")
public class EarlyWarningProperties {

    public record Range(double min, double max) {
    }

    private Map<String, Range> ranges = new HashMap<>();
    private Duration maxFutureSkew = Duration.ofMinutes(5);
    private Duration maxPastAge = Duration.ofDays(30);

    public Map<String, Range> getRanges() {
        return ranges;
    }

    public void setRanges(Map<String, Range> ranges) {
        this.ranges = ranges;
    }

    public Duration getMaxFutureSkew() {
        return maxFutureSkew;
    }

    public void setMaxFutureSkew(Duration maxFutureSkew) {
        this.maxFutureSkew = maxFutureSkew;
    }

    public Duration getMaxPastAge() {
        return maxPastAge;
    }

    public void setMaxPastAge(Duration maxPastAge) {
        this.maxPastAge = maxPastAge;
    }
}

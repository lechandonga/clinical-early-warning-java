package com.github.lechandonga.clinicalearlywarningjava.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {

    /** 系统 UTC 时钟，测试可注入固定时钟以保证时间相关用例可重复。 */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}

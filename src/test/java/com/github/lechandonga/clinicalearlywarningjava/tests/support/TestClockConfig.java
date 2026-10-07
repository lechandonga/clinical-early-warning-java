package com.github.lechandonga.clinicalearlywarningjava.tests.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;

@TestConfiguration
public class TestClockConfig {

    public static final MutableClock CLOCK = new MutableClock(Instant.parse("2026-10-07T18:00:00Z"));

    @Bean
    @Primary
    public Clock fixedClock() {
        return CLOCK;
    }
}

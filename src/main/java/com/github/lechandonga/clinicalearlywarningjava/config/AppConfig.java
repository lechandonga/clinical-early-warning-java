package com.github.lechandonga.clinicalearlywarningjava.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ClinicalWarningProperties.class)
public class AppConfig {
}

package com.whoshot.nhl.datajob.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;

/**
 * Activates Spring Framework's {@code @Retryable} and {@code @ConcurrencyLimit} method annotations.
 */
@Configuration
@EnableResilientMethods
public class ResilienceConfig {
}

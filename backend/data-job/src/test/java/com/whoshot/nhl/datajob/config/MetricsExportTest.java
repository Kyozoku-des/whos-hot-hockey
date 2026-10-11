package com.whoshot.nhl.datajob.config;

import io.micrometer.registry.otlp.OtlpMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.otlp.OtlpMetricsExportAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** The OTLP push to the monitoring agent is opt-in, and points at the agent when enabled. */
class MetricsExportTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    MetricsAutoConfiguration.class, OtlpMetricsExportAutoConfiguration.class));

    @Test
    void pushIsOffByDefault() {
        runner.withPropertyValues(appProperty("management.otlp.metrics.export.enabled"))
                .run(context -> assertThat(context).doesNotHaveBean(OtlpMeterRegistry.class));
    }

    @Test
    void enablingPushCreatesTheOtlpRegistry() {
        runner.withPropertyValues("management.otlp.metrics.export.enabled=true",
                        appProperty("management.otlp.metrics.export.url"))
                .run(context -> assertThat(context).hasSingleBean(OtlpMeterRegistry.class));
    }

    @Test
    void defaultsTargetTheAgentAndTagTheApplication() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.config.location",
                        Path.of("src", "main", "resources").toAbsolutePath().toUri().toString())
                .withProperty("BACKEND_ENV_FILE", Path.of("missing.env").toAbsolutePath().toString());
        ConfigDataEnvironmentPostProcessor.applyTo(environment);

        assertThat(environment.getProperty("management.otlp.metrics.export.enabled")).isEqualTo("false");
        assertThat(environment.getProperty("management.otlp.metrics.export.url"))
                .isEqualTo("http://alloy:4318/v1/metrics");
        assertThat(environment.getProperty("management.metrics.tags.application")).isEqualTo("nhl-data-job");
        assertThat(environment.getProperty("management.metrics.enable.http.client.requests")).isEqualTo("false");
    }

    /** The real application.properties value of {@code name}, with its defaults resolved. */
    private static String appProperty(String name) {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.config.location",
                        Path.of("src", "main", "resources").toAbsolutePath().toUri().toString())
                .withProperty("BACKEND_ENV_FILE", Path.of("missing.env").toAbsolutePath().toString());
        ConfigDataEnvironmentPostProcessor.applyTo(environment);
        return name + "=" + environment.getProperty(name);
    }
}

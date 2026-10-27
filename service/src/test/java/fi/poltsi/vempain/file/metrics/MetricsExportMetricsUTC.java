package fi.poltsi.vempain.file.metrics;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.registry.otlp.OtlpMetricsSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * The OTLP push reports itself: outcome counters with the HTTP status, duration, bytes and the age of the last success.
 */
class MetricsExportMetricsUTC {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-26T10:00:00Z"));
    private SimpleMeterRegistry registry;
    private MetricsExportMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new MetricsExportMetrics(new Clock() {

            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        });
        metrics.bindTo(registry);
    }

    @Test
    void successfulPushesCountBytesAndResetTheAgeOfTheLastSuccess() {
        assertThat(registry.get(MetricsExportMetrics.EXPORT_LAST_SUCCESS_AGE)
            .gauge()
            .value()).isEqualTo(-1);

        metrics.exportSucceeded(2048, Duration.ofMillis(120));
        now.set(now.get()
            .plusSeconds(45));

        assertThat(registry.get(MetricsExportMetrics.EXPORT_REQUESTS)
            .tags("result", "success", "status", "ok")
            .counter()
            .count()).isEqualTo(1);
        assertThat(registry.get(MetricsExportMetrics.EXPORT_BYTES)
            .counter()
            .count()).isEqualTo(2048);
        assertThat(registry.get(MetricsExportMetrics.EXPORT_DURATION)
            .tag("result", "success")
            .timer()
            .totalTime(TimeUnit.MILLISECONDS)).isEqualTo(120);
        assertThat(registry.get(MetricsExportMetrics.EXPORT_LAST_SUCCESS_AGE)
            .gauge()
            .value()).isEqualTo(45);
        assertThat(registry.get(MetricsExportMetrics.EXPORT_METERS)
            .gauge()
            .value()).isEqualTo(registry.getMeters()
                .size());
    }

    @Test
    void rejectedPushesCarryTheHttpStatusAndUnreachableCollectorsCountAsErrors() {
        metrics.exportFailed(new IllegalStateException("Server responded with HTTP status code 401 and body Unauthorized"), Duration.ofMillis(30));
        metrics.exportFailed(new RuntimeException("wrapped", new IllegalStateException("Server responded with HTTP status code 503 and body ")),
                Duration.ofMillis(10));
        metrics.exportFailed(new IOException("Connection refused"), Duration.ofSeconds(10));

        assertThat(registry.get(MetricsExportMetrics.EXPORT_REQUESTS)
            .tags("result", "rejected", "status", "401")
            .counter()
            .count()).isEqualTo(1);
        assertThat(registry.get(MetricsExportMetrics.EXPORT_REQUESTS)
            .tags("result", "rejected", "status", "503")
            .counter()
            .count()).isEqualTo(1);
        assertThat(registry.get(MetricsExportMetrics.EXPORT_REQUESTS)
            .tags("result", "error", "status", "none")
            .counter()
            .count()).isEqualTo(1);
        assertThat(registry.get(MetricsExportMetrics.EXPORT_DURATION)
            .tag("result", "error")
            .timer()
            .count()).isEqualTo(1);
        assertThat(registry.find(MetricsExportMetrics.EXPORT_BYTES)
            .counter()).isNull();
        assertThat(registry.get(MetricsExportMetrics.EXPORT_LAST_SUCCESS_AGE)
            .gauge()
            .value()).isEqualTo(-1);
    }

    @Test
    void meteredSenderReportsTheDelegateOutcomeAndRethrowsFailures() throws Exception {
        var delegate = mock(OtlpMetricsSender.class);
        var sender = new MeteredOtlpMetricsSender(delegate, metrics);
        var request = OtlpMetricsSender.Request.builder(new byte[512])
            .address("https://collector/v1/metrics")
            .build();

        sender.send(request);
        verify(delegate).send(request);
        assertThat(registry.get(MetricsExportMetrics.EXPORT_BYTES)
            .counter()
            .count()).isEqualTo(512);

        doThrow(new IOException("down")).when(delegate)
            .send(any());
        assertThatThrownBy(() -> sender.send(request)).isInstanceOf(IOException.class)
            .hasMessage("down");
        assertThat(registry.get(MetricsExportMetrics.EXPORT_REQUESTS)
            .tags("result", "error", "status", "none")
            .counter()
            .count()).isEqualTo(1);
    }

    @Test
    void registrarWrapsOnlyTheOtlpSenderBean() {
        var registrar = new MeteredOtlpMetricsSender.Registrar(
                new org.springframework.beans.factory.support.StaticListableBeanFactory(java.util.Map.of("metricsExportMetrics", metrics))
                    .getBeanProvider(MetricsExportMetrics.class));
        var delegate = mock(OtlpMetricsSender.class);

        var wrapped = registrar.postProcessAfterInitialization(delegate, "otlpMetricsSender");
        assertThat(wrapped).isInstanceOf(MeteredOtlpMetricsSender.class);
        assertThat(registrar.postProcessAfterInitialization(wrapped, "otlpMetricsSender")).isSameAs(wrapped);
        assertThat(registrar.postProcessAfterInitialization("other", "other")).isEqualTo("other");
    }
}

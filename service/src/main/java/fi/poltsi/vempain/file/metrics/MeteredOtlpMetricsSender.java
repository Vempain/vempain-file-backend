package fi.poltsi.vempain.file.metrics;

import java.time.Duration;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import io.micrometer.registry.otlp.OtlpMetricsSender;
import lombok.RequiredArgsConstructor;

/**
 * Decorates the {@link OtlpMetricsSender} Spring Boot builds for the OTLP registry so that every push is reported to {@link MetricsExportMetrics}. The registry
 * keeps its own error handling: failures are recorded and rethrown unchanged.
 */
@RequiredArgsConstructor
public class MeteredOtlpMetricsSender implements OtlpMetricsSender {

    private final OtlpMetricsSender delegate;
    private final MetricsExportMetrics metrics;

    @Override
    public void send(Request request) throws Exception {
        var payloadBytes = request.getMetricsData() == null ? 0 : request.getMetricsData().length;
        var started = System.nanoTime();
        try {
            delegate.send(request);
            metrics.exportSucceeded(payloadBytes, Duration.ofNanos(System.nanoTime() - started));
        } catch (Exception failure) {
            metrics.exportFailed(failure, Duration.ofNanos(System.nanoTime() - started));
            throw failure;
        }
    }

    /**
     * Wraps the auto-configured sender bean; a static nested post-processor so that Boot's OTLP auto-configuration stays untouched and the export settings
     * (URL, authentication, SSL bundle) keep coming from {@code management.otlp.metrics.export.*}.
     */
    @Component
    @RequiredArgsConstructor
    public static class Registrar implements BeanPostProcessor {

        private final ObjectProvider<MetricsExportMetrics> metrics;

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) {
            if (bean instanceof OtlpMetricsSender sender
                && !(bean instanceof MeteredOtlpMetricsSender)) {
                return new MeteredOtlpMetricsSender(sender, metrics.getObject());
            }
            return bean;
        }
    }
}

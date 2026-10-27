package fi.poltsi.vempain.file.metrics;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;

/**
 * Meters about the OTLP metrics push itself, so that the monitoring environment shows whether this backend is still exporting, how often the push fails and how
 * big it is. {@link MeteredOtlpMetricsSender} reports every push here; as a {@link MeterBinder} this component registers its meters on every registry Spring
 * Boot creates. Names are {@code vempain.metrics_export.*}; the {@code result} tag is {@code success}, {@code rejected} (the collector answered with a non-2xx
 * status, kept in {@code status}) or {@code error} (no response at all, {@code status=none}).
 * <p>
 * A failed push cannot report itself while the failure lasts; the counters are cumulative, so the failures become visible with the next successful push, and
 * {@code vempain.metrics_export.last_success.age.seconds} shows how long the gap was.
 */
@Component
public class MetricsExportMetrics implements MeterBinder {

    public static final String EXPORT_REQUESTS = "vempain.metrics_export.requests";
    public static final String EXPORT_DURATION = "vempain.metrics_export.duration";
    public static final String EXPORT_BYTES = "vempain.metrics_export.bytes";
    public static final String EXPORT_LAST_SUCCESS_AGE = "vempain.metrics_export.last_success.age.seconds";
    public static final String EXPORT_METERS = "vempain.metrics_export.meters";

    public static final String RESULT_SUCCESS = "success";
    public static final String RESULT_REJECTED = "rejected";
    public static final String RESULT_ERROR = "error";
    /** {@code status} tag of a successful push (the sender does not expose the exact 2xx code). */
    public static final String STATUS_OK = "ok";
    /** {@code status} tag of a push that got no HTTP response. */
    public static final String STATUS_NONE = "none";

    /** The OTLP sender of Micrometer reports a non-2xx answer as an exception whose message carries the status code. */
    private static final Pattern STATUS_CODE = Pattern.compile("HTTP status code (\\d{3})");

    private final Clock clock;
    private final CompositeMeterRegistry sink = new CompositeMeterRegistry();
    private final AtomicReference<Instant> lastSuccess = new AtomicReference<>();

    @Autowired
    public MetricsExportMetrics() {
        this(Clock.systemUTC());
    }

    /**
     * @param clock the clock the age of the last successful push is measured with; tests pass a controllable one
     */
    public MetricsExportMetrics(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        sink.add(registry);
        Gauge.builder(EXPORT_LAST_SUCCESS_AGE, this, MetricsExportMetrics::lastSuccessAgeSeconds)
            .description("Seconds since the collector last accepted a metrics push; -1 before the first success")
            .strongReference(true)
            .register(registry);
        Gauge.builder(EXPORT_METERS, registry, r -> r.getMeters()
            .size())
            .description("Meters in the registry, which is the size of every push")
            .register(registry);
    }

    /**
     * The collector accepted a push.
     *
     * @param payloadBytes the size of the serialized metrics
     * @param duration     how long the request took
     */
    public void exportSucceeded(long payloadBytes, Duration duration) {
        lastSuccess.set(clock.instant());
        record(RESULT_SUCCESS, STATUS_OK, duration);
        if (payloadBytes > 0) {
            Counter.builder(EXPORT_BYTES)
                .description("Bytes of serialized metrics the collector accepted")
                .baseUnit("bytes")
                .register(sink)
                .increment(payloadBytes);
        }
    }

    /**
     * A push failed: either the collector rejected it (the failure names the HTTP status) or it could not be reached at all.
     */
    public void exportFailed(Throwable failure, Duration duration) {
        var status = rejectedStatus(failure);
        record(status.isPresent() ? RESULT_REJECTED : RESULT_ERROR, status.orElse(STATUS_NONE), duration);
    }

    /**
     * Seconds since the last successful push, or -1 when no push has succeeded since the start.
     */
    public long lastSuccessAgeSeconds() {
        var at = lastSuccess.get();
        return at == null ? -1
                : Math.max(0, Duration.between(at, clock.instant())
                    .getSeconds());
    }

    static Optional<String> rejectedStatus(Throwable failure) {
        for (var cause = failure; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            var matcher = STATUS_CODE.matcher(String.valueOf(cause.getMessage()));
            if (matcher.find()) {
                return Optional.of(matcher.group(1));
            }
        }
        return Optional.empty();
    }

    private void record(String result, String status, Duration duration) {
        Counter.builder(EXPORT_REQUESTS)
            .description("Metrics pushes to the collector, by outcome and HTTP status")
            .tags("result", result, "status", status)
            .register(sink)
            .increment();
        Timer.builder(EXPORT_DURATION)
            .description("Duration of a metrics push")
            .tag("result", result)
            .register(sink)
            .record(duration == null
                    || duration.isNegative() ? Duration.ZERO : duration);
    }
}

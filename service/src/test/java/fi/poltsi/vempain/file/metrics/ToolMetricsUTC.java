package fi.poltsi.vempain.file.metrics;

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The static tool timer records on the global registry, with the result of the call as a tag.
 */
class ToolMetricsUTC {

    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        Metrics.addRegistry(registry);
    }

    @AfterEach
    void tearDown() {
        Metrics.removeRegistry(registry);
    }

    @Test
    void successfulCallIsTimedAndItsValueReturned() throws IOException {
        var value = ToolMetrics.time(ToolMetrics.FFMPEG, () -> "json");

        assertThat(value).isEqualTo("json");
        assertThat(registry.get(ToolMetrics.TOOL_DURATION)
            .tags("tool", "ffmpeg", "result", "success")
            .timer()
            .count()).isEqualTo(1);
    }

    @Test
    void failingCallIsTimedAsFailureAndRethrown() {
        var exception = assertThrows(IOException.class, () -> ToolMetrics.time(ToolMetrics.FFMPEG, () -> {
            throw new IOException("codec missing");
        }));

        assertThat(exception.getMessage()).isEqualTo("codec missing");
        assertThat(registry.get(ToolMetrics.TOOL_DURATION)
            .tags("tool", "ffmpeg", "result", "failure")
            .timer()
            .count()).isEqualTo(1);
    }

    @Test
    void recordAddsOneTimedRun() {
        ToolMetrics.record(ToolMetrics.FFMPEG, ToolMetrics.RESULT_FAILURE, 5_000_000L);

        assertThat(registry.get(ToolMetrics.TOOL_DURATION)
            .tags("tool", "ffmpeg", "result", "failure")
            .timer()
            .totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isEqualTo(5);
    }
}

package fi.poltsi.vempain.file.metrics;

import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Metrics;

/**
 * Timer of the external tools (exiftool, the bundled FFmpeg) that the static tool classes run. They have no Spring context, so the timer is recorded on the
 * global registry, which Spring Boot binds the application registry to.
 */
public final class ToolMetrics {

    public static final String TOOL_DURATION = "vempain.tool.duration";
    public static final String EXIFTOOL = "exiftool";
    public static final String FFMPEG = "ffmpeg";
    public static final String RESULT_SUCCESS = "success";
    public static final String RESULT_FAILURE = "failure";

    private ToolMetrics() {}

    /**
     * Records one run of {@code tool} that took {@code nanos}.
     */
    public static void record(String tool, String result, long nanos) {
        Metrics.timer(TOOL_DURATION, "tool", tool, "result", result)
            .record(nanos, TimeUnit.NANOSECONDS);
    }

    /**
     * Runs {@code call} and records its duration with the success or failure result; the exception of a failed call is rethrown unchanged.
     */
    public static <T, E extends Exception> T time(String tool, ThrowingSupplier<T, E> call) throws E {
        var start = System.nanoTime();
        var result = RESULT_FAILURE;
        try {
            var value = call.get();
            result = RESULT_SUCCESS;
            return value;
        } finally {
            record(tool, result, System.nanoTime() - start);
        }
    }

    @FunctionalInterface
    public interface ThrowingSupplier<T, E extends Exception> {

        T get() throws E;
    }
}

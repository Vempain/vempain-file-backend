package fi.poltsi.vempain.file.feign;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.sun.net.httpserver.HttpServer;

import fi.poltsi.vempain.admin.api.Constants;
import io.micrometer.tracing.Tracer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * With tracing enabled the Feign clients forward the W3C {@code traceparent} of the current span to the admin backend, so that a request of the file backend
 * and the admin side work it triggers share one trace. No OTLP endpoint is configured in this test, so no span leaves the JVM; only the propagation is
 * exercised against a stub admin backend.
 */
@SpringBootTest(properties = {
    "vempain.app.frontend-url=http://localhost:3000",
    "vempain.original-root-directory=/tmp",
    "vempain.export-root-directory=/tmp",
    "vempain.generate-missing-thumbnails.batch-size=10",
    "vempain.generate-missing-thumbnails.thumb-image-quality=0.5",
    "vempain.generate-missing-thumbnails.thumb-image-size=100",
    // export.enabled is the switch Boot's tracing conditions look at (propagation included); without an OTLP endpoint nothing is sent anywhere
    "management.tracing.enabled=true",
    "management.tracing.export.enabled=true",
    "management.tracing.sampling.probability=1.0" })
class VempainAdminTracePropagationITC {

    private static HttpServer adminStub;
    private static final Map<String, String> RECEIVED_HEADERS = new ConcurrentHashMap<>();

    @Autowired
    private VempainAdminDataClient dataClient;
    @Autowired
    private Tracer tracer;

    @BeforeAll
    static void startAdminStub() throws IOException {
        adminStub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        adminStub.createContext("/api", exchange -> {
            exchange.getRequestHeaders()
                .forEach((name, values) -> RECEIVED_HEADERS.put(name.toLowerCase(), String.join(",", values)));
            var body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders()
                .add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        adminStub.start();
    }

    @AfterAll
    static void stopAdminStub() {
        adminStub.stop(0);
    }

    @DynamicPropertySource
    static void adminBackendUrl(DynamicPropertyRegistry registry) {
        registry.add("vempain.service.admin-backend-url", () -> "http://127.0.0.1:"
                + adminStub.getAddress()
                    .getPort()
                + "/api");
    }

    @BeforeEach
    void clearHeaders() {
        RECEIVED_HEADERS.clear();
    }

    @Test
    void feignCallCarriesTheTraceparentOfTheCurrentSpanAndTheApiToken() {
        var span = tracer.nextSpan()
            .name("file backend request")
            .start();
        try (var ignored = tracer.withSpan(span)) {
            dataClient.getDataSetByIdentifier("music_library");
        } catch (RuntimeException e) {
            // The stub answers an empty object; whatever Feign makes of it, the request has been sent with its headers
        } finally {
            span.end();
        }

        var traceparent = RECEIVED_HEADERS.get("traceparent");
        // version-traceid-spanid-flags; the flags carry the sampled bit (and the random trace id bit of trace context level 2)
        assertThat(traceparent).as("W3C trace context forwarded to the admin backend: %s", RECEIVED_HEADERS)
            .matches("00-"
                    + span.context()
                        .traceId()
                    + "-[0-9a-f]{16}-0[0-3]");
        assertThat(traceparent).as("the Feign call is a client span of its own, a child of the caller's span")
            .doesNotContain("-"
                    + span.context()
                        .spanId()
                    + "-");
        assertThat(RECEIVED_HEADERS).containsKey(Constants.API_TOKEN_HEADER.toLowerCase());
    }

    @Test
    void feignCallWithoutACurrentSpanStartsATraceOfItsOwn() {
        try {
            dataClient.getDataSetByIdentifier("music_library");
        } catch (RuntimeException e) {
            // see above
        }

        assertThat(RECEIVED_HEADERS.get("traceparent")).as("the client span is the root of a new trace: %s", RECEIVED_HEADERS)
            .matches("00-[0-9a-f]{32}-[0-9a-f]{16}-0[0-3]");
        assertThat(RECEIVED_HEADERS).containsKey(Constants.API_TOKEN_HEADER.toLowerCase());
    }
}

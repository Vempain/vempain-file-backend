package fi.poltsi.vempain.file.feign;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import feign.RequestInterceptor;
import fi.poltsi.vempain.admin.api.Constants;

/**
 * Every call to the admin backend carries the service-to-service API token (created in the admin UI under API tokens and configured as
 * {@code vempain.service.admin-backend-api-token}) in the {@link Constants#API_TOKEN_HEADER} header. No login, no JWT, no password.
 * <p>
 * With tracing on, every call also carries the W3C {@code traceparent} of a client span that is a child of the current span (request or background task), so
 * that the admin side work joins the trace. Nothing here does that: OpenFeign registers {@code MicrometerObservationCapability} in every client's context
 * ({@code feign-micrometer}), and the propagating observation handler of Micrometer Tracing injects the context. {@code VempainAdminTracePropagationITC} pins
 * the header against a stub admin backend; do not add a hand-written interceptor for it.
 */
@Configuration
public class VempainAdminFeignConfig {

    @Value("${vempain.service.admin-backend-api-token}")
    private String adminApiToken;

    @Bean
    public RequestInterceptor apiTokenRequestInterceptor() {
        return template -> template.header(Constants.API_TOKEN_HEADER, adminApiToken);
    }
}

package fi.poltsi.vempain.file.security;

import java.util.List;

import jakarta.servlet.Filter;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

import fi.poltsi.vempain.auth.security.jwt.AuthEntryPointJwt;
import fi.poltsi.vempain.auth.service.UserDetailsServiceImpl;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class WebSecurityConfig extends fi.poltsi.vempain.auth.security.WebSecurityConfig {

    private final ApiTokenAuthenticationFilter apiTokenAuthenticationFilter;

    public WebSecurityConfig(UserDetailsServiceImpl userDetailsServiceImpl, AuthEntryPointJwt authEntryPointJwt, Environment environment,
            ApiTokenAuthenticationFilter apiTokenAuthenticationFilter) {
        super(userDetailsServiceImpl, authEntryPointJwt, environment);
        this.apiTokenAuthenticationFilter = apiTokenAuthenticationFilter;
    }

    /**
     * Read-only API tokens ({@code X-Vempain-Api-Token}) are checked before the JWT filter; see {@link ApiTokenAuthenticationFilter}.
     */
    @Override
    protected List<Filter> additionalAuthenticationFilters() {
        return List.of(apiTokenAuthenticationFilter);
    }

    @Override
    protected void configureApplicationAuthorization(ApplicationAuthorizationConfigurer authorization) {
        authorization.authenticated("/files/**", "/file-groups/**", "/tags/**", "/metadata/**", "/scan-files/**", "/publish/**", "/data-publish/**",
                "/location/**", "/path-completion/**", "/statistics/**", "/tasks/**",
                // The clustered map of the GPS-tagged files (MapAPI of vempain-common)
                "/map/**",
                // User, unit and ACL management hosted from vempain-auth-core (administrator ACL checked in the controllers)
                "/content-management/**",
                // Read-only API token management (administrator ACL checked in the controller)
                "/api-tokens/**",
                // The system-wide read view of vempain-mcp; only an MCP_READ API token gets past ApiTokenAuthenticationFilter
                "/mcp", "/mcp/**");
    }
}

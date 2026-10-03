package fi.poltsi.vempain.file.security;

import fi.poltsi.vempain.auth.security.jwt.AuthEntryPointJwt;
import fi.poltsi.vempain.auth.service.UserDetailsServiceImpl;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class WebSecurityConfig extends fi.poltsi.vempain.auth.security.WebSecurityConfig {

	public WebSecurityConfig(UserDetailsServiceImpl userDetailsServiceImpl, AuthEntryPointJwt authEntryPointJwt, Environment environment) {
		super(userDetailsServiceImpl, authEntryPointJwt, environment);
	}

	@Override
	protected void configureApplicationAuthorization(ApplicationAuthorizationConfigurer authorization) {
		authorization.authenticated(HttpMethod.GET, "/files/*/content");
		authorization.authenticated(HttpMethod.GET, "/tags/**");
		authorization.authenticated(HttpMethod.POST, "/tags/paged", "/tags/*/files/paged");
		authorization.hasRole("ADMIN", HttpMethod.GET, "/files/**", "/file-groups/**");
		authorization.hasRole("ADMIN", HttpMethod.POST, "/scan-files/**", "/publish/**", "/data-publish/**",
							  "/file-groups/**", "/tags", "/tags/files/**", "/tags/all/**", "/location/guard/**");
		authorization.hasRole("ADMIN", HttpMethod.PUT, "/file-groups/**", "/tags", "/location/guard/**");
		authorization.hasRole("ADMIN", HttpMethod.DELETE, "/files/**", "/file-groups/**", "/tags/**", "/location/guard/**");
	}
}

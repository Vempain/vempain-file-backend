package fi.poltsi.vempain.file.security;

import fi.poltsi.vempain.auth.security.jwt.AuthEntryPointJwt;
import fi.poltsi.vempain.auth.service.UserDetailsServiceImpl;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
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
		authorization.authenticated(
				"/files/**",
				"/file-groups/**",
				"/tags/**",
				"/scan-files/**",
				"/publish/**",
				"/data-publish/**",
				"/location/**",
				"/path-completion/**",
				"/statistics/**",
				"/tasks/**");
	}
}

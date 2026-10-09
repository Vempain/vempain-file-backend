package fi.poltsi.vempain.file.feign;

import feign.RequestInterceptor;
import fi.poltsi.vempain.admin.api.Constants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Every call to the admin backend carries the service-to-service API token (created in the admin UI under API tokens and configured
 * as {@code vempain.service.admin-backend-api-token}) in the {@link Constants#API_TOKEN_HEADER} header. No login, no JWT, no password.
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

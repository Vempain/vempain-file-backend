package fi.poltsi.vempain.file.feign;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VempainAdminFeignConfig {

	@Bean
	public RequestInterceptor bearerAuthRequestInterceptor(VempainAdminTokenProvider vempainAdminTokenProvider) {
		return template -> template.header("Authorization", "Bearer " + vempainAdminTokenProvider.getToken());
	}
}

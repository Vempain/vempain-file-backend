package fi.poltsi.vempain.file.feign;

import feign.RequestTemplate;
import fi.poltsi.vempain.admin.api.Constants;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class VempainAdminFeignConfigUTC {

	@Test
	void everyAdminCallCarriesTheApiTokenHeaderAndNoBearer() {
		var config = new VempainAdminFeignConfig();
		ReflectionTestUtils.setField(config, "adminApiToken", "vat_configured-token");
		var template = new RequestTemplate();

		config.apiTokenRequestInterceptor()
			  .apply(template);

		assertEquals(java.util.List.of("vat_configured-token"), java.util.List.copyOf(template.headers()
																							  .get(Constants.API_TOKEN_HEADER)));
		assertFalse(template.headers()
							.containsKey("Authorization"));
	}
}

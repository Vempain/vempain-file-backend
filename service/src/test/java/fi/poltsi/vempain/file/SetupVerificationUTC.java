package fi.poltsi.vempain.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SetupVerificationUTC {

	@ParameterizedTest
	@ValueSource(strings = {
			"jdbc:postgresql://db:5432/vempain?sslmode=require",
			"jdbc:postgresql://db:5432/vempain?currentSchema=vempain&sslmode=verify-ca",
			"jdbc:postgresql://db:5432/vempain?sslmode=VERIFY-FULL&currentSchema=vempain",
			"jdbc:postgresql://db:5432/vempain?ssl=true",
			"jdbc:postgresql://db:5432/vempain?ssl"
	})
	void requiresTls_acceptsUrlsThatInsistOnTls(String url) {
		assertTrue(SetupVerification.requiresTls(url));
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {
			"jdbc:postgresql://db:5432/vempain",
			"jdbc:postgresql://db:5432/vempain?currentSchema=vempain",
			"jdbc:postgresql://db:5432/vempain?sslmode=prefer",
			"jdbc:postgresql://db:5432/vempain?sslmode=allow",
			"jdbc:postgresql://db:5432/vempain?sslmode=disable",
			"jdbc:postgresql://db:5432/vempain?ssl=false",
			"jdbc:postgresql://db:5432/vempain?useSSL=true",
			"jdbc:postgresql://db:5432/vempain?sslmode=disable&ssl=true"
	})
	void requiresTls_rejectsUrlsThatAllowPlaintext(String url) {
		assertFalse(SetupVerification.requiresTls(url));
	}

	@Test
	void verifyProductionSettings_acceptsTlsDatasourceInProd() {
		var context = contextWith("prod", "jdbc:postgresql://db:5432/vempain_file?sslmode=require");

		new SetupVerification(context).verifyProductionSettings(new ContextRefreshedEvent(context));

		verify(context, never()).close();
	}

	@Test
	void verifyProductionSettings_closesApplicationForPlaintextCapableDatasourceInProd() {
		var context = contextWith("prod", "jdbc:postgresql://db:5432/vempain_file?sslmode=prefer");

		new SetupVerification(context).verifyProductionSettings(new ContextRefreshedEvent(context));

		verify(context).close();
	}

	@Test
	void verifyProductionSettings_ignoresOtherProfiles() {
		var context = contextWith("local", "jdbc:postgresql://localhost:5432/vempain_file");

		new SetupVerification(context).verifyProductionSettings(new ContextRefreshedEvent(context));

		verify(context, never()).close();
	}

	private static ConfigurableApplicationContext contextWith(String profile, String datasourceUrl) {
		var environment = new MockEnvironment().withProperty("spring.datasource.url", datasourceUrl)
											   .withProperty("vempain.service.admin-backend-url", "https://admin.example/api");
		environment.setActiveProfiles(profile);
		var context = mock(ConfigurableApplicationContext.class);
		when(context.getEnvironment()).thenReturn(environment);
		return context;
	}
}

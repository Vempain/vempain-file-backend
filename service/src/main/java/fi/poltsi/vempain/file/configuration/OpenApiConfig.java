package fi.poltsi.vempain.file.configuration;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Collections;

@SecurityScheme(
		name = "Bearer Authentication",
		type = SecuritySchemeType.HTTP,
		bearerFormat = "JWT",
		scheme = "bearer"
)
@Configuration
public class OpenApiConfig {
	final         Environment environment;
	private final String      devName;
	private final String      devUrl;
	private final String      devEmail;
	private final String      devLicense;
	private final String      devLicenseUrl;
	private final String      devVersion;
	private final String      serverDescription;

	public OpenApiConfig(Environment environment) {
		this.environment       = environment;
		this.devName           = environment.getProperty("vempain.developer.name");
		this.devUrl            = environment.getProperty("vempain.developer.url");
		this.devEmail          = environment.getProperty("vempain.developer.email");
		this.devLicense        = environment.getProperty("vempain.license");
		this.devLicenseUrl     = environment.getProperty("vempain.license-url");
		this.devVersion        = environment.getProperty("vempain.version");
		this.serverDescription = environment.getProperty("server.description");
	}

	@Bean
	public OpenAPI customOpenApi() {
		var contact = new Contact()
				.name(devName)
				.url(devUrl)
				.email(devEmail);

		var license = new License()
				.name(devLicense)
				.url(devLicenseUrl);

		var server = new Server()
				.url("/")
				.description(serverDescription);
		return new OpenAPI()
				.components(new Components())
				.servers(Collections.singletonList(server))
				.info(new Info()
							  .title("Vempain File application REST API")
							  .description("Vempain File Spring Boot application")
							  .version(devVersion)
							  .contact(contact)
							  .license(license));
	}
}

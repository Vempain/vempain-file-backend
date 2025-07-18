package fi.poltsi.vempain.file;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@OpenAPIDefinition(info = @Info(version = "${vempain.version}",
								title = "${vempain.description}",
								license = @io.swagger.v3.oas.annotations.info.License(name = "${vempain.license}",
																					  url = "${vempain.license-url}")),
				   servers = @Server(url = "http://localhost:8080/api", description = "current server"))
@SpringBootApplication
public class VempainFileServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(VempainFileServiceApplication.class, args);
	}

}

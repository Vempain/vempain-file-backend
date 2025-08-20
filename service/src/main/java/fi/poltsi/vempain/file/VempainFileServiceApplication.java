package fi.poltsi.vempain.file;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@OpenAPIDefinition(info = @Info(version = "${vempain.version}",
								title = "${vempain.description}",
								license = @io.swagger.v3.oas.annotations.info.License(name = "${vempain.license}",
																					  url = "${vempain.license-url}")),
				   servers = @Server(url = "http://localhost:8080/api", description = "current server"))
@EnableJpaRepositories(basePackages = {"fi.poltsi.vempain.auth.repository", "fi.poltsi.vempain.file.repository"})
@EntityScan(basePackages = {"fi.poltsi.vempain.auth.entity", "fi.poltsi.vempain.file.entity"})
@SpringBootApplication(scanBasePackages = {
		"fi.poltsi.vempain.file",
		"fi.poltsi.vempain.auth",
})
@EnableFeignClients(basePackages = "fi.poltsi.vempain.file.feign")
public class VempainFileServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(VempainFileServiceApplication.class, args);
	}

}

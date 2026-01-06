package fi.poltsi.vempain.file;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

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

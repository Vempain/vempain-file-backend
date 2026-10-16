package fi.poltsi.vempain.file;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;

import static org.mockito.Mockito.mockStatic;

/**
 * Unit test (UTC) for the application entry point: {@code main} hands the arguments to Spring Boot.
 */
class VempainFileServiceApplicationUTC {

    @Test
    void mainDelegatesToSpringApplication() {
        try (var spring = mockStatic(SpringApplication.class)) {
            VempainFileServiceApplication.main(new String[]{ "--spring.main.banner-mode=off" });

            spring.verify(() -> SpringApplication.run(VempainFileServiceApplication.class, "--spring.main.banner-mode=off"));
        }
    }
}

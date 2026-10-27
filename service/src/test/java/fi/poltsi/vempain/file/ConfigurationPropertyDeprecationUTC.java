package fi.poltsi.vempain.file;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;

import org.junit.jupiter.api.Test;

import fi.poltsi.vempain.common.config.DeprecatedConfigurationProperties;
import fi.poltsi.vempain.common.config.DeprecatedConfigurationProperties.Finding;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fails the build when an application configuration file binds a Spring Boot property that the Boot version of the build marks deprecated. Boot only logs such
 * keys at startup, so this is the configuration counterpart of the {@code -Werror} javac setting: the key has to be replaced by the one the finding names.
 */
class ConfigurationPropertyDeprecationUTC {

    @Test
    void applicationConfigurationBindsNoDeprecatedSpringBootProperties() throws IOException {
        var files = new ArrayList<Path>();
        files.addAll(DeprecatedConfigurationProperties.configurationFiles(Path.of("src", "main", "resources")));
        files.addAll(DeprecatedConfigurationProperties.configurationFiles(Path.of("src", "test", "resources")));
        assertThat(files).as("the application files are found from the module directory the tests run in")
            .isNotEmpty();

        var findings = DeprecatedConfigurationProperties.scan(files);

        assertThat(findings).as("deprecated configuration keys: %s", findings.stream()
            .map(Finding::describe)
            .toList())
            .isEmpty();
    }
}

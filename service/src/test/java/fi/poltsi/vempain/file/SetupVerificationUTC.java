package fi.poltsi.vempain.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

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
        "jdbc:postgresql://db:5432/vempain?ssl" })
    void requiresTlsAcceptsUrlsThatInsistOnTls(String url) {
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
        "jdbc:postgresql://db:5432/vempain?sslmode=disable&ssl=true" })
    void requiresTlsRejectsUrlsThatAllowPlaintext(String url) {
        assertFalse(SetupVerification.requiresTls(url));
    }

    @Test
    void verifyProductionSettingsAcceptsTlsDatasourceInProd() {
        var context = contextWith("prod", "jdbc:postgresql://db:5432/vempain_file?sslmode=require");

        new SetupVerification(context).verifyProductionSettings(new ContextRefreshedEvent(context));

        verify(context, never()).close();
    }

    @Test
    void verifyProductionSettingsClosesApplicationForPlaintextCapableDatasourceInProd() {
        var context = contextWith("prod", "jdbc:postgresql://db:5432/vempain_file?sslmode=prefer");

        new SetupVerification(context).verifyProductionSettings(new ContextRefreshedEvent(context));

        verify(context).close();
    }

    @Test
    void verifyProductionSettingsIgnoresOtherProfiles() {
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

    private static ConfigurableApplicationContext contextWithEssentials(String jwtSecret, String frontendUrl, String originalRoot) {
        var environment = new MockEnvironment().withProperty("vempain.app.frontend-url", frontendUrl)
            .withProperty("vempain.original-root-directory", originalRoot)
            .withProperty("vempain.export-root-directory", "/tmp")
            .withProperty("vempain.service.admin-backend-url", "https://admin.example/api")
            .withProperty("vempain.service.admin-backend-api-token", "vat_token");
        if (jwtSecret != null) {
            environment.setProperty("vempain.app.jwt-secret", jwtSecret);
        }
        var context = mock(ConfigurableApplicationContext.class);
        when(context.getEnvironment()).thenReturn(environment);
        return context;
    }

    @Test
    void checkEssentialConfigurationsAcceptsACompleteConfiguration() {
        var context = contextWithEssentials("secret", "http://localhost:3000", "/tmp");

        new SetupVerification(context).checkEssentialConfigurations(new ContextRefreshedEvent(context));

        verify(context, never()).close();
    }

    @Test
    void checkEssentialConfigurationsClosesTheApplicationForMissingDefaultOrNonexistentValues() {
        var missing = contextWithEssentials(null, "http://localhost:3000", "/tmp");
        new SetupVerification(missing).checkEssentialConfigurations(new ContextRefreshedEvent(missing));
        verify(missing).close();

        var defaulted = contextWithEssentials("secret", "override-me", "/tmp");
        new SetupVerification(defaulted).checkEssentialConfigurations(new ContextRefreshedEvent(defaulted));
        verify(defaulted).close();

        var nonexistent = contextWithEssentials("secret", "http://localhost:3000", "/nonexistent-root-" + java.util.UUID.randomUUID());
        new SetupVerification(nonexistent).checkEssentialConfigurations(new ContextRefreshedEvent(nonexistent));
        verify(nonexistent).close();
    }

    @Test
    void verifyProductionSettingsClosesApplicationForAPlainHttpAdminUrlInProd() {
        var environment = new MockEnvironment().withProperty("spring.datasource.url", "jdbc:postgresql://db:5432/vempain_file?sslmode=require")
            .withProperty("vempain.service.admin-backend-url", "http://admin.example/api");
        environment.setActiveProfiles("prod");
        var context = mock(ConfigurableApplicationContext.class);
        when(context.getEnvironment()).thenReturn(environment);

        new SetupVerification(context).verifyProductionSettings(new ContextRefreshedEvent(context));

        verify(context).close();
    }

    @Test
    void printAllConfigurationListsThePropertyNamesWithoutFailing() {
        var context = contextWith("local", "jdbc:postgresql://localhost:5432/vempain_file");

        new SetupVerification(context).printAllConfiguration(new ContextRefreshedEvent(context));

        verify(context, never()).close();
    }

    @Test
    void checkEssentialConfigurationsValidatesNumbersFilesAndRejectsUnknownTypes() throws Exception {
        var executable = java.nio.file.Files.createTempFile("setup-verification", ".sh");
        executable.toFile()
            .setExecutable(true);
        var plain = java.nio.file.Files.createTempFile("setup-verification", ".txt");
        plain.toFile()
            .setExecutable(false);
        try {
            var valid = contextWithEssentials("secret", "http://localhost:3000", "/tmp");
            ((MockEnvironment) valid.getEnvironment()).withProperty("a.number", "42")
                .withProperty("a.file", executable.toString());
            var verification = new SetupVerification(valid);
            ReflectionTestUtils.setField(verification, "requiredKeys", new String[][]{ { "a.number", "number" }, { "a.file", "file" } });
            verification.checkEssentialConfigurations(new ContextRefreshedEvent(valid));
            verify(valid, never()).close();

            var invalid = contextWithEssentials("secret", "http://localhost:3000", "/tmp");
            ((MockEnvironment) invalid.getEnvironment()).withProperty("a.number", "forty-two")
                .withProperty("a.missing", "/nonexistent-" + java.util.UUID.randomUUID())
                .withProperty("a.directory", "/tmp")
                .withProperty("a.plain", plain.toString())
                .withProperty("a.unknown", "x");
            var failing = new SetupVerification(invalid);
            ReflectionTestUtils.setField(failing, "requiredKeys",
                    new String[][]{
                        { "a.number", "number" },
                        { "a.missing", "file" },
                        { "a.directory", "file" },
                        { "a.plain", "file" },
                        { "a.unknown", "colour" } });
            failing.checkEssentialConfigurations(new ContextRefreshedEvent(invalid));
            verify(invalid, org.mockito.Mockito.atLeast(5)).close();
        } finally {
            java.nio.file.Files.deleteIfExists(executable);
            java.nio.file.Files.deleteIfExists(plain);
        }
    }
}

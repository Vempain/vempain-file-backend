package fi.poltsi.vempain.file;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.StreamSupport;

import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@AllArgsConstructor
class SetupVerification implements ApplicationContextAware {

    private static final String TYPE_STRING = "string";
    private static final String TYPE_NUMBER = "number";
    private static final String TYPE_PATH = "path";
    private static final String TYPE_FILE = "file";

    private final String[][] requiredKeys = {
        { "vempain.app.frontend-url", TYPE_STRING },
        { "vempain.app.jwt-secret", TYPE_STRING },
        { "vempain.original-root-directory", TYPE_PATH },
        { "vempain.export-root-directory", TYPE_PATH },
        { "vempain.service.admin-backend-url", TYPE_STRING },
        { "vempain.service.admin-backend-api-token", TYPE_STRING } };

    private static final Set<String> TLS_SSL_MODES = Set.of("require", "verify-ca", "verify-full");

    private ApplicationContext applicationContext;

    @EventListener
    public void checkEssentialConfigurations(ContextRefreshedEvent event) {
        final Environment env = event.getApplicationContext()
            .getEnvironment();

        for (String[] keyPair : requiredKeys) {
            var value = env.getProperty(keyPair[0]);
            log.debug("Verifying that key {} is defined and not empty", keyPair[0]);

            if (value == null
                || value.isEmpty()) {
                closeApplication("Missing configuration value for key: " + keyPair[0]);
            } else if (value.equals("override-me")) {
                closeApplication("Configuration value for key " + keyPair[0] + " is still set to default value");
            } else {
                var path = Paths.get(value);

                switch (keyPair[1]) {
                    case TYPE_NUMBER:
                        if (!NumberUtils.isCreatable(value)) {
                            closeApplication("Failed to parse number from configuration " + keyPair[0]);
                        }

                        break;
                    case TYPE_PATH:
                        if (!Files.exists(path)) {
                            closeApplication("Path from configuration " + keyPair[0] + " does not exist");
                        }
                        break;
                    case TYPE_FILE:
                        if (!Files.exists(path)) {
                            closeApplication("File from configuration " + keyPair[0] + " pointing to " + value + " does not exist");
                        }

                        if (!Files.isRegularFile(path)) {
                            closeApplication("File from configuration " + keyPair[0] + " pointing to " + value + " is not a file");
                        }

                        if (!Files.isExecutable(path)) {
                            closeApplication("File from configuration " + keyPair[0] + " pointing to " + value + " is not executable");
                        }
                        break;
                    case TYPE_STRING:
                        // For now we don't do any verification, might split this further to other types
                        break;
                    default :
                        closeApplication("Unknown configuration type: " + keyPair[1]);
                }
            }
        }
    }

    @EventListener
    public void verifyProductionSettings(ContextRefreshedEvent event) {
        final Environment env = event.getApplicationContext()
            .getEnvironment();
        if (!env.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }

        var adminUrl = env.getProperty("vempain.service.admin-backend-url", "");
        if (!"https".equalsIgnoreCase(URI.create(adminUrl)
            .getScheme())) {
            closeApplication("Production Admin backend URL must use HTTPS");
            return;
        }

        if (!requiresTls(env.getProperty("spring.datasource.url", ""))) {
            closeApplication("Production datasource URL must require TLS: set sslmode=require, verify-ca or verify-full");
        }
    }

    /**
     * Tells whether a PostgreSQL JDBC URL makes the driver insist on TLS: {@code sslmode} is require, verify-ca or verify-full, or {@code ssl} is given without
     * an {@code sslmode} (the driver then verifies the full certificate). Weaker modes such as prefer or allow fall back to plaintext when the server offers no
     * TLS, so they do not count.
     */
    static boolean requiresTls(String jdbcUrl) {
        var queryStart = jdbcUrl == null ? -1 : jdbcUrl.indexOf('?');
        if (queryStart < 0) {
            return false;
        }

        String sslMode = null;
        var ssl = false;
        for (var parameter : jdbcUrl.substring(queryStart + 1)
            .split("&")) {
            var separator = parameter.indexOf('=');
            var name = (separator < 0 ? parameter : parameter.substring(0, separator)).trim()
                .toLowerCase(Locale.ROOT);
            var value = separator < 0 ? ""
                    : URLDecoder.decode(parameter.substring(separator + 1), StandardCharsets.UTF_8)
                        .trim()
                        .toLowerCase(Locale.ROOT);
            if ("sslmode".equals(name)) {
                sslMode = value;
            } else if ("ssl".equals(name)) {
                ssl = !"false".equals(value);
            }
        }

        return sslMode == null ? ssl : TLS_SSL_MODES.contains(sslMode);
    }

    private void closeApplication(String message) {
        log.error(message);
        log.error("Shutting down application");
        ((ConfigurableApplicationContext) applicationContext).close();
    }

    @EventListener
    public void printAllConfiguration(ContextRefreshedEvent event) {
        final Environment env = event.getApplicationContext()
            .getEnvironment();
        log.debug("====== Environment and configuration ======");
        log.debug("Active profiles: {}", Arrays.toString(env.getActiveProfiles()));
        final MutablePropertySources sources = ((AbstractEnvironment) env).getPropertySources();
        List<String> propertyNames = StreamSupport.stream(sources.spliterator(), false)
            .filter(ps -> ps instanceof EnumerablePropertySource)
            .map(ps -> ((EnumerablePropertySource<?>) ps).getPropertyNames())
            .flatMap(Arrays::stream)
            .distinct()
            .filter(prop -> !(prop.contains("credentials")
                              || prop.contains("password")))
            .sorted()
            .toList();

        propertyNames.forEach(prop -> log.debug("Configuration key available: {}", prop));
        log.debug("===========================================");
    }

    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}

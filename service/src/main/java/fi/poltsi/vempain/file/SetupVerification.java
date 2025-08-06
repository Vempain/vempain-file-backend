package fi.poltsi.vempain.file;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.StreamSupport;

@Slf4j
@Component
@AllArgsConstructor
class SetupVerification implements ApplicationContextAware {
	private final String TYPE_STRING = "string";
	private final String TYPE_NUMBER = "number";
	private final String TYPE_PATH   = "path";
	private final String TYPE_FILE   = "file";

	private final String[][] requiredKeys = {
			{"vempain.app.frontend-url", TYPE_STRING},
			{"vempain.original-root-directory", TYPE_PATH},
			{"vempain.export-root-directory", TYPE_PATH}
	};

	private ApplicationContext applicationContext;

	@EventListener
	public void checkEssentialConfigurations(ContextRefreshedEvent event) {
		final Environment env = event.getApplicationContext()
									 .getEnvironment();

		for (String[] keyPair : requiredKeys) {
			var value = env.getProperty(keyPair[0]);
			log.info("Verifying that key {} is defined and not empty: {}", keyPair[0], value);

			if (value == null || value.isEmpty()) {
				closeApplication("Missing configuration value for key: " + keyPair[0]);
			} else if (value.equals("override-me")) {
				closeApplication("Configuration value for key " + keyPair[0] + " is still set to default value");
			} else {
				var path = Paths.get(value);

				switch (keyPair[1]) {
					case TYPE_NUMBER:
						if (!NumberUtils.isCreatable(value)) {
							closeApplication("Failed to parse number from configuration " + keyPair[0] + " value " + value);
						}

						break;
					case TYPE_PATH:
						if (!Files.exists(path)) {
							closeApplication("Path from configuration " + keyPair[0] + " pointing to " + value + " does not exist");
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
					default:
						closeApplication("Unknown configuration type: " + keyPair[1]);
				}
			}
		}
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
		log.info("====== Environment and configuration ======");
		log.info("Active profiles: {}", Arrays.toString(env.getActiveProfiles()));
		final MutablePropertySources sources = ((AbstractEnvironment) env).getPropertySources();
		List<String> propertyNames = StreamSupport.stream(sources.spliterator(), false)
												  .filter(ps -> ps instanceof EnumerablePropertySource)
												  .map(ps -> ((EnumerablePropertySource<?>) ps).getPropertyNames())
												  .flatMap(Arrays::stream)
												  .distinct()
												  .filter(prop -> !(prop.contains("credentials") || prop.contains("password")))
												  .sorted()
												  .toList();

		propertyNames.forEach(prop -> printProperty(env, prop));
		log.info("===========================================");
	}


	private void printProperty(Environment env, String key) {
		try {
			log.info("{}: {}", key, env.getProperty(key));
		} catch (Exception e) {
			log.error("Failed to fetch property value for {}", key);
		}
	}

	@Override
	public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}
}

package com.bradandmarsha.wisehomeindex.config;

import com.bradandmarsha.wisehomeindex.model.ApplicationEntry;
import com.bradandmarsha.wisehomeindex.model.IndexConfig;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * Loads the application index configuration from a YAML file.
 *
 * <p>The file location is resolved in the following order:</p>
 * <ol>
 *   <li>System property {@code wise.home.index.config}</li>
 *   <li>Environment variable {@code WISE_HOME_INDEX_CONFIG}</li>
 *   <li>The bundled {@code applications.yaml} on the classpath (sample fallback)</li>
 * </ol>
 */
public final class ConfigLoader {

    private static final Logger LOG = Logger.getLogger(ConfigLoader.class.getName());

    public static final String SYSTEM_PROPERTY = "wise.home.index.config";
    public static final String ENV_VARIABLE = "WISE_HOME_INDEX_CONFIG";
    public static final String CLASSPATH_FALLBACK = "applications.yaml";

    private ConfigLoader() {
    }

    /**
     * Resolves and loads the configuration using the standard lookup order.
     *
     * @return the parsed and validated {@link IndexConfig}
     */
    public static IndexConfig load() {
        String configuredPath = resolveConfiguredPath();
        if (configuredPath != null && !configuredPath.isBlank()) {
            Path path = Path.of(configuredPath.trim());
            if (Files.isReadable(path)) {
                LOG.info(() -> "Loading wise-home-index configuration from " + path.toAbsolutePath());
                try (InputStream in = Files.newInputStream(path)) {
                    return parse(in);
                } catch (IOException ex) {
                    throw new IllegalStateException("Unable to read configuration file: " + path.toAbsolutePath(), ex);
                }
            }
            LOG.warning(() -> "Configured path " + path.toAbsolutePath()
                    + " is not readable; falling back to bundled " + CLASSPATH_FALLBACK);
        }

        LOG.info(() -> "Loading bundled " + CLASSPATH_FALLBACK);
        try (InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(CLASSPATH_FALLBACK)) {
            if (in == null) {
                throw new IllegalStateException("Bundled configuration '" + CLASSPATH_FALLBACK + "' was not found on the classpath");
            }
            return parse(in);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read bundled configuration '" + CLASSPATH_FALLBACK + "'", ex);
        }
    }

    /**
     * Parses YAML from the given stream into an {@link IndexConfig} and validates it.
     *
     * @param in the YAML input stream
     * @return the validated configuration
     */
    public static IndexConfig parse(InputStream in) {
        LoaderOptions options = new LoaderOptions();
        Yaml yaml = new Yaml(new Constructor(IndexConfig.class, options));
        IndexConfig config = yaml.load(in);
        if (config == null) {
            config = new IndexConfig();
        }
        validate(config);
        return config;
    }

    private static String resolveConfiguredPath() {
        String fromProperty = System.getProperty(SYSTEM_PROPERTY);
        if (fromProperty != null && !fromProperty.isBlank()) {
            return fromProperty;
        }
        return System.getenv(ENV_VARIABLE);
    }

    private static void validate(IndexConfig config) {
        int index = 0;
        for (ApplicationEntry entry : config.getApplications()) {
            if (entry.getName() == null || entry.getName().isBlank()) {
                throw new IllegalStateException("Application at index " + index + " is missing the required 'name' field");
            }
            if (entry.getUrl() == null || entry.getUrl().isBlank()) {
                throw new IllegalStateException("Application '" + entry.getName() + "' is missing the required 'url' field");
            }
            index++;
        }
    }
}

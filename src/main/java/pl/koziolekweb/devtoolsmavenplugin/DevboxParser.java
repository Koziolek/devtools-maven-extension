package pl.koziolekweb.devtoolsmavenplugin;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Named;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses {@code devbox.json} (Jetify Devbox; JSONC, so comments and trailing commas are tolerated).
 * The {@code packages} field is either an array of {@code name@version} strings or an object
 * mapping names to a version string or a {@code {"version": ...}} object. Every versioned package
 * is exposed as {@code devbox.<name>}. Entries without a version and flake references are skipped.
 * Never throws on bad content: {@link JsonFiles} logs an unreadable or invalid file at ERROR with
 * its path, an unusable entry is skipped (DEBUG).
 */
@Named
@Singleton
final class DevboxParser implements ToolParser {

    private static final String FILE_NAME = "devbox.json";
    private static final String PREFIX = "devbox.";
    private static final String PACKAGES = "packages";
    private static final String VERSION = "version";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public String propertyPrefix() {
        return PREFIX;
    }

    @Override
    public Map<String, String> parse(Path root) {
        Path path = root.resolve(FILE_NAME);
        Object packages = JsonFiles.readJson(path).get(PACKAGES);

        Map<String, String> values = new LinkedHashMap<>();
        if (packages instanceof List) {
            for (Object entry : (List<?>) packages) {
                if (entry instanceof String) {
                    parseArrayEntry((String) entry, values);
                } else {
                    logger.debug("Skipping non-string {} entry in {}", PACKAGES, FILE_NAME);
                }
            }
        } else if (packages instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) packages).entrySet()) {
                parseObjectEntry(String.valueOf(entry.getKey()), entry.getValue(), values);
            }
        } else if (packages != null) {
            logger.debug("Ignoring {} of unsupported type in {}", PACKAGES, FILE_NAME);
        }
        return values;
    }

    private void parseArrayEntry(String raw, Map<String, String> values) {
        String entry = raw.trim();
        if (entry.indexOf('#') >= 0 || entry.indexOf(':') >= 0) {
            logger.debug("Skipping flake reference '{}' in {}", entry, FILE_NAME);
            return;
        }
        int at = entry.indexOf('@', 1);
        if (at < 0) {
            logger.debug("Skipping unversioned package '{}' in {}", entry, FILE_NAME);
            return;
        }
        put(values, entry.substring(0, at), entry.substring(at + 1));
    }

    private void parseObjectEntry(String name, Object value, Map<String, String> values) {
        Object version = value;
        if (value instanceof Map) {
            version = ((Map<?, ?>) value).get(VERSION);
        }
        if (version instanceof String) {
            put(values, name, (String) version);
        } else {
            logger.debug("Skipping package '{}' without a version in {}", name, FILE_NAME);
        }
    }

    private void put(Map<String, String> values, String name, String version) {
        String key = name.trim();
        String value = version.trim();
        if (key.isEmpty() || value.isEmpty()) {
            logger.debug("Skipping package with empty name or version in {}", FILE_NAME);
            return;
        }
        values.put(PREFIX + key, value);
    }
}

package pl.koziolekweb.devtoolsmavenplugin;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Named;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses {@code .mvn/wrapper/maven-wrapper.properties} (a {@link Properties} file, so escaped
 * characters such as {@code https\://} are unescaped). Every entry is exposed as {@code
 * mvnw.<name>}; in addition {@code mvnw.maven} carries the Maven version derived from {@code
 * distributionUrl} when it matches {@code apache-maven-<version>-bin|src}. Never throws on bad
 * content: an unreadable file (invalid UTF-8, a malformed unicode escape, permissions) is logged at
 * ERROR with its path and ignored as a whole; a {@code distributionUrl} without a Maven version is
 * logged at WARN.
 */
@Named
@Singleton
final class MavenWrapperParser implements ToolParser {

    private static final String FILE_NAME = ".mvn/wrapper/maven-wrapper.properties";
    private static final String PREFIX = "mvnw.";
    private static final String DISTRIBUTION_URL = "distributionUrl";

    private static final Pattern MAVEN_VERSION =
            Pattern.compile("apache-maven-(\\d[^/\\\\]*?)-(?:bin|src)\\b");

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public String propertyPrefix() {
        return PREFIX;
    }

    @Override
    public Map<String, String> parse(Path root) {
        Path path = root.resolve(FILE_NAME);
        if (!Files.isRegularFile(path)) {
            return Collections.emptyMap();
        }

        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IllegalArgumentException | IOException e) {
            logger.error("Error reading file: {}", path, e);
            return Collections.emptyMap();
        }

        Map<String, String> values = new LinkedHashMap<>();
        for (String name : new TreeSet<>(properties.stringPropertyNames())) {
            String value = properties.getProperty(name).trim();
            if (!name.trim().isEmpty() && !value.isEmpty()) {
                values.put(PREFIX + name.trim(), value);
            }
        }

        String url = values.get(PREFIX + DISTRIBUTION_URL);
        if (url != null) {
            Matcher matcher = MAVEN_VERSION.matcher(url);
            if (matcher.find()) {
                values.put(PREFIX + "maven", matcher.group(1));
            } else {
                logger.warn(
                        "Cannot derive Maven version from {} in {}", DISTRIBUTION_URL, FILE_NAME);
            }
        }
        return values;
    }
}

package pl.koziolekweb.devtoolsmavenplugin;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.inject.Named;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses the {@code "volta"} object of {@code package.json}. Every string entry (node, npm, yarn,
 * pnpm, ...) is exposed as {@code volta.<name>}; non-string values and the {@code extends} key are
 * ignored. Never throws on bad content: {@link JsonFiles} logs an unreadable or invalid file at
 * ERROR with its path, an unusable entry is skipped (DEBUG). The file is read leniently, so
 * comments and trailing commas in {@code package.json} are tolerated.
 */
@Named
@Singleton
final class VoltaParser implements ToolParser {

    static final String FILE_NAME = "package.json";
    private static final String SECTION = "volta";
    private static final String EXTENDS = "extends";
    private static final String PREFIX = "volta.";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public String propertyPrefix() {
        return PREFIX;
    }

    @Override
    public Map<String, String> parse(Path root) {
        Path path = root.resolve(FILE_NAME);
        Object section = JsonFiles.readJsonSection(path, SECTION);

        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) section).entrySet()) {
            String name = String.valueOf(entry.getKey()).trim();
            Object value = entry.getValue();
            if (EXTENDS.equals(name)) {
                logger.debug("Ignoring '{}' in {} '{}' section", EXTENDS, FILE_NAME, SECTION);
            } else if (!(value instanceof String)) {
                logger.debug("Ignoring non-string '{}' in {} '{}'", name, FILE_NAME, SECTION);
            } else if (!name.isEmpty() && !((String) value).trim().isEmpty()) {
                values.put(PREFIX + name, ((String) value).trim());
            }
        }
        return values;
    }
}

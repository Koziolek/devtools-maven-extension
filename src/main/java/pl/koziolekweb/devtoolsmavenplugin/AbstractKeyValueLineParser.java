package pl.koziolekweb.devtoolsmavenplugin;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base for version-pin files made of one {@code key<separator>value} entry per line (the key and
 * value being single tokens). Resolves {@code fileName} under the reactor root, returns an empty
 * map if it is absent, otherwise reads it line by line, skipping blank lines and {@code #} comment
 * lines, splits each remaining line on the first {@code separator}, and finally applies {@code
 * propertyPrefix} to every resulting key. A malformed line does not fail the build — it is logged
 * at WARN and skipped.
 *
 * <p>This is only one of the supported shapes. Files with a different structure (a single value,
 * TOML, JSON, properties, ...) have their own parsers; a subclass whose line syntax differs
 * overrides {@link #parseLine(String, Map)}.
 */
abstract class AbstractKeyValueLineParser implements ToolParser, WithLineReader {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final String fileName;
    private final String propertyPrefix;
    private final char separator;

    AbstractKeyValueLineParser(String fileName, String propertyPrefix, char separator) {
        this.fileName = fileName;
        this.propertyPrefix = propertyPrefix;
        this.separator = separator;
    }

    @Override
    public final String propertyPrefix() {
        return propertyPrefix;
    }

    @Override
    public final Map<String, String> parse(Path root) {
        return readAndProcessLines(
                root,
                fileName,
                lines -> {
                    Map<String, String> raw = new LinkedHashMap<>();
                    for (int i = 0; i < lines.size(); i++) {
                        String line = lines.get(i).trim();
                        if (line.isEmpty() || line.startsWith("#")) {
                            continue;
                        }
                        if (!parseLine(line, raw)) {
                            logger.warn(
                                    "Ignoring malformed line {} in {}: '{}'",
                                    i + 1,
                                    fileName,
                                    line);
                        }
                    }

                    Map<String, String> values = new LinkedHashMap<>();
                    for (Map.Entry<String, String> entry : raw.entrySet()) {
                        values.put(propertyPrefix + entry.getKey(), entry.getValue());
                    }
                    return values;
                });
    }

    /**
     * Splits one non-blank, non-comment line on the first {@link #separator}. On success, adds the
     * resulting (unprefixed) entry to {@code values} and returns {@code true}. On a malformed line
     * (no separator, or an empty key/value), adds nothing and returns {@code false} — the caller
     * logs a WARN and moves on, no exception involved.
     *
     * <p>Default semantics (used by {@code .sdkmanrc}): no inline-comment handling, a repeated key
     * overwrites the earlier one (last wins). Subclasses whose format differs override this hook.
     */
    boolean parseLine(String line, Map<String, String> values) {
        int index = line.indexOf(separator);
        if (index <= 0) {
            return false;
        }

        String key = line.substring(0, index).trim();
        String value = line.substring(index + 1).trim();
        if (key.isEmpty() || value.isEmpty()) {
            return false;
        }

        values.put(key, value);
        return true;
    }
}

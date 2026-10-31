package pl.koziolekweb.devtoolsmavenplugin;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Named;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads the {@code [tools]} table of {@code mise.toml} and {@code .mise.toml} under the reactor
 * root, parsed with the {@code toml-java} library. Keys are prefixed with {@code mise.}. A tool
 * value may be a string, a number, an array (first element wins, also across several lines) or a
 * table with a {@code version} (inline, or as {@code [tools.node]}); anything else is logged at
 * WARN and skipped. If both files define the same key, {@code mise.toml} wins.
 *
 * <p>TOML is validated as a whole: a file with a syntax error is ignored as a whole, {@link
 * TomlFiles} logs it at ERROR with the file name, and the other file is still read. This parser
 * never throws on bad content.
 */
@Named
@Singleton
final class MiseParser implements ToolParser {

    static final String PREFIX = "mise.";
    static final String SECTION = "tools";

    /** Ordered by precedence, highest first. */
    static final String[] FILE_NAMES = {"mise.toml", ".mise.toml"};

    private final Logger logger = LoggerFactory.getLogger(MiseParser.class);

    /**
     * The version of a tool value: a string or number as is, the first element of an array, the
     * {@code version} of a table; {@code null} if there is none.
     */
    private static String versionOf(Object value) {
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            return list.isEmpty() ? null : versionOf(list.get(0));
        }
        if (value instanceof Map) {
            return versionOf(((Map<?, ?>) value).get("version"));
        }
        return TomlFiles.scalarText(value);
    }

    @Override
    public String propertyPrefix() {
        return PREFIX;
    }

    @Override
    public Map<String, String> parse(Path root) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String fileName : FILE_NAMES) {
            Path path = root.resolve(fileName);
            Map<String, Object> document = TomlFiles.readToml(path);
            addTools(document.get(SECTION), fileName, values);
        }
        return Collections.unmodifiableMap(values);
    }

    private void addTools(Object tools, String fileName, Map<String, String> values) {
        if (!(tools instanceof Map)) {
            return;
        }
        for (Map.Entry<?, ?> tool : ((Map<?, ?>) tools).entrySet()) {
            String version = versionOf(tool.getValue());
            if (version == null) {
                logger.warn("Ignoring tool '{}' in {}: no usable version", tool.getKey(), fileName);
            } else {
                values.putIfAbsent(PREFIX + tool.getKey(), version);
            }
        }
    }
}

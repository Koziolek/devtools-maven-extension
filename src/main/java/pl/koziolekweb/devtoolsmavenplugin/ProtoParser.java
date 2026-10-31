package pl.koziolekweb.devtoolsmavenplugin;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.inject.Named;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads {@code .prototools} (moonrepo proto) under the reactor root, parsed with the {@code
 * toml-java} library. Only the top-level {@code key = "value"} entries are used; every table
 * ({@code [plugins]}, {@code [settings]}, {@code [tools.x]}, an inline table, ...) is ignored. Keys
 * are prefixed with {@code proto.}. A string or a number is a version; other values (arrays,
 * booleans, dates) are logged at WARN and skipped.
 *
 * <p>TOML is validated as a whole: a file with a syntax error (or a duplicate key) is ignored as a
 * whole, {@link TomlFiles} logs it at ERROR with the file name. This parser never throws on bad
 * content.
 */
@Named
@Singleton
final class ProtoParser implements ToolParser {

    static final String PREFIX = "proto.";
    static final String FILE_NAME = ".prototools";

    private final Logger logger = LoggerFactory.getLogger(ProtoParser.class);

    @Override
    public String propertyPrefix() {
        return PREFIX;
    }

    @Override
    public Map<String, String> parse(Path root) {
        Path path = root.resolve(FILE_NAME);
        Map<String, Object> document = TomlFiles.readToml(path);
        Map<String, String> values = new LinkedHashMap<>();
        document.forEach((key, value) -> addEntry(key, value, values));
        return values;
    }

    private void addEntry(String key, Object value, Map<String, String> out) {
        if (value instanceof Map) {
            return;
        }
        String version = TomlFiles.scalarText(value);
        if (version == null) {
            logger.warn("Ignoring non-string value for '{}' in {}", key, FILE_NAME);
        } else {
            out.put(PREFIX + key, version);
        }
    }
}

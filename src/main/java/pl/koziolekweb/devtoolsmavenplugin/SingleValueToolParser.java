package pl.koziolekweb.devtoolsmavenplugin;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Shared parsing for a version-pin file that holds a single bare value and no key (e.g. jenv's
 * {@code .java-version}, nvm's {@code .nvmrc}). The key is fixed by the subclass: resolves {@code
 * fileName} under the reactor root, returns an empty map if it is absent, otherwise takes the first
 * line that is neither blank nor a {@code #} comment (trimmed, verbatim) as the value of {@code
 * propertyPrefix + key}. Any further lines are ignored, matching how these tools read their files.
 * A file with no usable line does not fail the build — it is logged at WARN and yields an empty
 * map.
 */
abstract class SingleValueToolParser implements ToolParser, WithLineReader {

    private final String fileName;
    private final String propertyPrefix;
    private final String key;

    SingleValueToolParser(String fileName, String propertyPrefix, String key) {
        this.fileName = fileName;
        this.propertyPrefix = propertyPrefix;
        this.key = key;
    }

    @Override
    public final String propertyPrefix() {
        return propertyPrefix;
    }

    @Override
    public final Map<String, String> parse(Path root) {
        return readAndProcessLines(root, fileName, this::apply);
    }

    private Map<String, String> apply(List<String> lines) {
        return lines.stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .limit(1)
                .collect(Collectors.toMap(line -> propertyPrefix + key, line -> line));
    }
}

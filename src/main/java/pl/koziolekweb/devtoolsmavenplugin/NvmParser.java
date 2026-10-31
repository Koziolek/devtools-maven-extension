package pl.koziolekweb.devtoolsmavenplugin;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.inject.Named;
import javax.inject.Singleton;

/**
 * Reads {@code .nvmrc} (nvm) and {@code .node-version} (fnm and others), both a single bare
 * version, into {@code nvm.node}.
 *
 * <p>Both files feed the same key, so one parser owns both and the precedence is fixed here rather
 * than left to the order in which Sisu injects parsers: {@code .nvmrc} wins, {@code .node-version}
 * is only a fallback (used when {@code .nvmrc} is absent or has no usable line). If both files
 * yield a value and the values differ, a WARN names both files and values. The value is taken
 * verbatim (a leading {@code v} stays; transforms strip it).
 */
@Named
@Singleton
final class NvmParser implements ToolParser, WithLineReader {

    static final String FILE_NAME = ".nvmrc";
    static final String FALLBACK_FILE_NAME = ".node-version";
    static final String PREFIX = "nvm.";
    static final String KEY = "node";

    @Override
    public String propertyPrefix() {
        return PREFIX;
    }

    @Override
    public Map<String, String> parse(Path root) {
        Optional<String> nvmrc = firstValue(root, FILE_NAME);
        Optional<String> nodeVersion = firstValue(root, FALLBACK_FILE_NAME);
        if (nvmrc.isPresent()
                && nodeVersion.isPresent()
                && !nvmrc.get().equals(nodeVersion.get())) {
            WLR_LOGGER.warn(
                    "{} ('{}') and {} ('{}') disagree; using {} for {}{}",
                    FILE_NAME,
                    nvmrc.get(),
                    FALLBACK_FILE_NAME,
                    nodeVersion.get(),
                    FILE_NAME,
                    PREFIX,
                    KEY);
        }
        return nvmrc.map(Optional::of)
                .orElse(nodeVersion)
                .map(v -> Collections.singletonMap(PREFIX + KEY, v))
                .orElseGet(Collections::emptyMap);
    }

    private Optional<String> firstValue(Path root, String fileName) {
        return readLines(root, fileName)
                .flatMap(
                        (List<String> lines) ->
                                lines.stream()
                                        .map(String::trim)
                                        .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                                        .findFirst());
    }
}

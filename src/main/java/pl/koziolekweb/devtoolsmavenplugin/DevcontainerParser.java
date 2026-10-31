package pl.koziolekweb.devtoolsmavenplugin;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.inject.Named;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses a dev container definition: {@code .devcontainer/devcontainer.json} and {@code
 * .devcontainer.json}. Both files are read and merged per feature; if both define the same feature,
 * the one in {@code .devcontainer/} wins, and an invalid file does not hide the other. The files
 * are JSONC, so comments and trailing commas are tolerated. For every entry of {@code "features"}
 * that has a {@code version} option (string or number), exposes {@code devcontainer.<name>}, where
 * {@code <name>} is the last path segment of the feature id without its {@code :tag} /
 * {@code @digest} suffix (e.g. {@code ghcr.io/devcontainers/features/java:1} gives {@code
 * devcontainer.java}). Never throws on bad content: {@link JsonFiles} logs an unreadable or invalid
 * file at ERROR with its path, an unusable feature is skipped (DEBUG).
 */
@Named
@Singleton
final class DevcontainerParser implements ToolParser {

    private static final String[] FILE_NAMES = {
        ".devcontainer/devcontainer.json", ".devcontainer.json"
    };
    private static final String PREFIX = "devcontainer.";
    private static final String FEATURES = "features";
    private static final String VERSION = "version";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public String propertyPrefix() {
        return PREFIX;
    }

    @Override
    public Map<String, String> parse(Path root) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String fileName : FILE_NAMES) {
            Path path = root.resolve(fileName);
            parseFile(path, fileName, values);
        }
        return values;
    }

    private Map<String, String> parseFile(Path path, String fileName, Map<String, String> values) {
        Map<?, ?> features = JsonFiles.readJsonSection(path, FEATURES);
        for (Map.Entry<?, ?> feature : (features).entrySet()) {
            String name = featureName(String.valueOf(feature.getKey()));
            String version = featureVersion(feature.getValue());
            if (name.isEmpty() || version == null) {
                logger.debug(
                        "Skipping feature '{}' in {}: no usable name/version",
                        feature.getKey(),
                        fileName);
                continue;
            }
            values.putIfAbsent(PREFIX + name, version);
        }
        return values;
    }

    private String featureName(String id) {
        String name = id.substring(id.lastIndexOf('/') + 1);
        int cut = name.length();
        for (char c : new char[] {':', '@'}) {
            int index = name.indexOf(c);
            if (index >= 0 && index < cut) {
                cut = index;
            }
        }
        return name.substring(0, cut).trim();
    }

    private String featureVersion(Object options) {
        if (!(options instanceof Map)) {
            return null;
        }
        Object version = ((Map<?, ?>) options).get(VERSION);
        String text = null;
        if (version instanceof String) {
            text = ((String) version).trim();
        } else {
            text = JsonFiles.numberText(version);
        }
        return text == null || text.isEmpty() ? null : text;
    }
}

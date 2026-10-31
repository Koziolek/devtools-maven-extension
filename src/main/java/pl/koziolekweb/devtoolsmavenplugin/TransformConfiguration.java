package pl.koziolekweb.devtoolsmavenplugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TransformConfiguration {

    private static final String TRANSFORM_PREFIX = "transform.";

    private final Properties configuration;
    private final List<String> namespaces;

    private TransformConfiguration(Properties configuration, List<String> namespaces) {
        this.configuration = configuration;
        this.namespaces = namespaces;
    }

    /**
     * Loads the transform configuration at {@code path}.
     *
     * @param path the {@code .mvn/devtools-extension.properties} file
     * @param namespaces the property prefixes of all registered parsers (e.g. {@code "sdkman."}),
     *     in the order a bare {@code source} is resolved against them; first hit wins. See {@link
     *     DevtoolsLifecycleParticipant#namespacesOf(List)}.
     * @return the loaded configuration
     * @throws IOException if the file cannot be read
     */
    static TransformConfiguration load(Path path, List<String> namespaces) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        }
        return new TransformConfiguration(properties, namespaces);
    }

    /**
     * Applies all configured transforms to {@code targetProperties} and returns the set of property
     * names it set or confirmed (whether newly written or already present via -D).
     */
    Set<String> apply(Properties targetProperties) {
        Set<String> appliedTargets = new LinkedHashSet<>();

        for (String target : executionOrder()) {
            String base = TRANSFORM_PREFIX + target + ".";
            String source = required(base + "source");
            String patternText = required(base + "pattern");
            String replacement = configuration.getProperty(base + "replacement", "$0");

            String sourceProperty = resolveSourceProperty(source, targetProperties);
            String sourceValue = targetProperties.getProperty(sourceProperty);
            if (sourceValue == null) {
                throw new IllegalArgumentException(
                        "Transformation '"
                                + target
                                + "' references missing property '"
                                + sourceProperty
                                + "'"
                                + (isQualified(sourceProperty) ? "" : " in any namespace"));
            }
            Pattern pattern;
            try {
                pattern = Pattern.compile(patternText);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Transformation '"
                                + target
                                + "' has invalid pattern '"
                                + patternText
                                + "': "
                                + e.getMessage(),
                        e);
            }
            Matcher matcher = pattern.matcher(sourceValue);
            if (!matcher.find()) {
                throw new IllegalArgumentException(
                        "Transformation '"
                                + target
                                + "' pattern does not match value '"
                                + sourceValue
                                + "'");
            }

            String transformed;
            try {
                transformed = matcher.replaceFirst(replacement);
            } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
                throw new IllegalArgumentException(
                        "Transformation '"
                                + target
                                + "' has invalid replacement '"
                                + replacement
                                + "' for pattern '"
                                + patternText
                                + "': "
                                + e.getMessage(),
                        e);
            }
            String namespace = sourceProperty.substring(0, sourceProperty.indexOf('.') + 1);
            String targetProperty = namespace + target;
            targetProperties.putIfAbsent(targetProperty, transformed);
            appliedTargets.add(targetProperty);
        }

        return appliedTargets;
    }

    /**
     * Orders transformations so that one whose source is the target of another runs after it
     * (topological sort; ties are broken alphabetically for determinism). Dependencies are matched
     * by name, ignoring the namespace prefix. A cycle is a configuration error.
     */
    private List<String> executionOrder() {
        Set<String> targets = transformTargets();
        Map<String, Set<String>> dependencies = new TreeMap<>();
        for (String target : targets) {
            String source = stripNamespace(required(TRANSFORM_PREFIX + target + ".source"));
            Set<String> deps = new TreeSet<>();
            if (targets.contains(source) && !source.equals(target)) {
                deps.add(source);
            }
            dependencies.put(target, deps);
        }

        List<String> order = new ArrayList<>();
        Set<String> remaining = new TreeSet<>(targets);
        while (!remaining.isEmpty()) {
            String next = null;
            for (String candidate : remaining) {
                if (!containsAny(remaining, dependencies.get(candidate))) {
                    next = candidate;
                    break;
                }
            }
            if (next == null) {
                throw new IllegalArgumentException(
                        "Circular dependency between transformations: " + remaining);
            }
            remaining.remove(next);
            order.add(next);
        }
        return order;
    }

    private static boolean containsAny(Set<String> set, Set<String> items) {
        for (String item : items) {
            if (set.contains(item)) {
                return true;
            }
        }
        return false;
    }

    private String stripNamespace(String source) {
        for (String namespace : namespaces) {
            if (source.startsWith(namespace)) {
                return source.substring(namespace.length());
            }
        }
        return source;
    }

    private Set<String> transformTargets() {
        Set<String> result = new TreeSet<>();
        for (Map.Entry<Object, Object> entry : configuration.entrySet()) {
            String name = String.valueOf(entry.getKey());
            if (!name.startsWith(TRANSFORM_PREFIX)) {
                continue;
            }
            String remainder = name.substring(TRANSFORM_PREFIX.length());
            int lastDot = remainder.lastIndexOf('.');
            if (lastDot > 0) {
                String field = remainder.substring(lastDot + 1);
                if ("source".equals(field)
                        || "pattern".equals(field)
                        || "replacement".equals(field)) {
                    result.add(remainder.substring(0, lastDot));
                }
            }
        }
        return result;
    }

    private boolean isQualified(String source) {
        for (String namespace : namespaces) {
            if (source.startsWith(namespace)) {
                return true;
            }
        }
        return false;
    }

    /**
     * An explicit {@code <namespace>.<key>} as is; a bare key in the first namespace that has it.
     */
    private String resolveSourceProperty(String source, Properties targetProperties) {
        if (isQualified(source)) {
            return source;
        }
        for (String namespace : namespaces) {
            String candidate = namespace + source;
            if (targetProperties.containsKey(candidate)) {
                return candidate;
            }
        }
        return source;
    }

    private String required(String key) {
        String value = configuration.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Missing required configuration property '" + key + "'");
        }
        return value;
    }
}

package pl.koziolekweb.devtoolsmavenplugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Named;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads {@code KEY=value} entries from {@code .env} and {@code .envrc} under the reactor root and
 * exposes them as {@code dotenv.<KEY>} (not {@code env.}, which would collide with Maven's {@code
 * ${env.X}}). When both files define a key, {@code .env} wins. A leading {@code export } is
 * stripped, values may be wrapped in matching single or double quotes (kept verbatim inside), and
 * unquoted values lose a trailing {@code # comment}. No variable interpolation is performed.
 *
 * <p>Only keys listed in {@code dotenv.allow} (a comma or whitespace separated list in {@value
 * DevtoolsLifecycleParticipant#CONFIG_FILE}) are exported, because {@code .env} files typically
 * hold secrets and every exported key ends up in Maven's user properties, visible in {@code mvn
 * -X}, {@code help:effective-pom}, CI logs and forked JVMs. The list {@code *} allows every key.
 * Without the setting nothing is exported; if a file has assignments but none is allowed, one INFO
 * line says so (key names only, never values).
 *
 * <p>Lines that are not {@code KEY=value} are skipped, never fatal: logged at DEBUG in {@code
 * .envrc} (it is a script, so direnv commands are expected), at WARN in {@code .env}.
 */
@Named
@Singleton
final class DotenvParser implements ToolParser, WithLineReader {

    private static final Logger LOGGER = LoggerFactory.getLogger(DotenvParser.class);

    private static final String PREFIX = "dotenv.";
    static final String ALLOW_PROPERTY = "dotenv.allow";
    private static final String ALLOW_ALL = "*";
    private static final String EXPORT = "export";
    private static final Pattern COMMENT = Pattern.compile("\\s#");
    private static final Pattern KEY = Pattern.compile("[A-Za-z_][A-Za-z0-9_.]*");
    // .env first, so putIfAbsent lets it win over .envrc
    private static final Object[][] FILE_NAMES = {{".env", true}, {".envrc", false}};

    @Override
    public String propertyPrefix() {
        return PREFIX;
    }

    @Override
    public Map<String, String> parse(Path root) {
        Map<String, String> values = new LinkedHashMap<>();
        for (Object[] file : FILE_NAMES) {
            final String fileName = (String) file[0];
            final boolean strict = (boolean) file[1];
            readAndProcessLines(
                            root,
                            fileName,
                            lines -> parseLines(lines, fileName, strict, allowedKeys(root)))
                    .forEach(values::putIfAbsent);
        }
        return Collections.unmodifiableMap(values);
    }

    /** Keys from {@code dotenv.allow}; empty when the setting or the config file is absent. */
    private static Set<String> allowedKeys(Path root) {
        Path configPath = root.resolve(DevtoolsLifecycleParticipant.CONFIG_FILE);
        if (!Files.isRegularFile(configPath)) {
            return Collections.emptySet();
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(configPath)) {
            properties.load(input);
        } catch (IOException | IllegalArgumentException e) {
            LOGGER.error("Error reading file: {}", configPath, e);
            return Collections.emptySet();
        }
        Set<String> allowed = new HashSet<>();
        for (String key : properties.getProperty(ALLOW_PROPERTY, "").split("[,\\s]+")) {
            if (!key.isEmpty()) {
                allowed.add(key);
            }
        }
        return allowed;
    }

    private static Map<String, String> parseLines(
            List<String> lines, String name, boolean strict, Set<String> allowed) {
        boolean allowAll = allowed.contains(ALLOW_ALL);
        Set<String> skipped = new TreeSet<>();
        Map<String, String> out = new LinkedHashMap<>();
        for (int i = 0; i < lines.size(); i++) {
            Map.Entry<String, String> entry =
                    parseAssignment(lines.get(i).trim(), name, i + 1, strict);
            if (entry == null) {
                continue;
            }
            if (allowAll || allowed.contains(entry.getKey())) {
                out.putIfAbsent(PREFIX + entry.getKey(), entry.getValue());
            } else {
                skipped.add(entry.getKey());
            }
        }
        if (out.isEmpty() && !skipped.isEmpty()) {
            LOGGER.info(
                    "No variable from {} is allowed, ignoring {}. List the ones to export in {} "
                            + "({}=KEY1,KEY2, or * for all).",
                    name,
                    skipped,
                    DevtoolsLifecycleParticipant.CONFIG_FILE,
                    ALLOW_PROPERTY);
        }
        return out;
    }

    /**
     * One trimmed line as key and value; {@code null} for a blank line, comment, malformed line
     * (reported) or an empty value.
     */
    private static Map.Entry<String, String> parseAssignment(
            String trimmed, String name, int lineNo, boolean strict) {
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return null;
        }
        String line = stripExport(trimmed);
        int eq = line.indexOf('=');
        if (eq < 0) {
            report(strict, "no '='", name, lineNo, line);
            return null;
        }
        String key = line.substring(0, eq).trim();
        if (!KEY.matcher(key).matches()) {
            report(strict, "invalid key", name, lineNo, line);
            return null;
        }
        String value = parseValue(line.substring(eq + 1).trim());
        return value.isEmpty() ? null : new AbstractMap.SimpleImmutableEntry<>(key, value);
    }

    private static String stripExport(String line) {
        if (line.startsWith(EXPORT)
                && line.length() > EXPORT.length()
                && Character.isWhitespace(line.charAt(EXPORT.length()))) {
            return line.substring(EXPORT.length()).trim();
        }
        return line;
    }

    private static String parseValue(String raw) {
        if (raw.length() >= 2) {
            char first = raw.charAt(0);
            if (first == '"' || first == '\'') {
                int close = raw.indexOf(first, 1);
                if (close > 0) {
                    String rest = raw.substring(close + 1).trim();
                    if (rest.isEmpty() || rest.startsWith("#")) {
                        return raw.substring(1, close);
                    }
                }
            }
        }
        if (raw.startsWith("#")) {
            return "";
        }
        Matcher comment = COMMENT.matcher(raw);
        return comment.find() ? raw.substring(0, comment.start()).trim() : raw;
    }

    private static void report(boolean strict, String why, String name, int lineNo, String line) {
        if (strict) {
            LOGGER.warn("Ignoring malformed line {} in {} ({}): '{}'", lineNo, name, why, line);
        } else {
            LOGGER.debug(
                    "Ignoring non-assignment line {} in {} ({}): '{}'", lineNo, name, why, line);
        }
    }
}

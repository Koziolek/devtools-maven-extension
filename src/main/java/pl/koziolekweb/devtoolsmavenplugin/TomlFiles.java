package pl.koziolekweb.devtoolsmavenplugin;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.vieiro.toml.TOML;
import net.vieiro.toml.TOMLParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared TOML reading for the TOML-based parsers. Not a Sisu component. The TOML library is only
 * touched when a file actually exists and is read, so a project without these files pays no
 * class-loading cost.
 *
 * <p>TOML is parsed as a whole: a syntax error anywhere (also a duplicate key) makes the library
 * report errors and no usable tree, so a file is either read completely or not at all. Nothing is
 * thrown: an unreadable file or an invalid document is logged at {@code ERROR} with the path of the
 * file (and, for a syntax error, the line and column of each error) and yields an empty map, so a
 * parser needs no error handling of its own.
 */
final class TomlFiles {

    private static final Logger logger = LoggerFactory.getLogger(TomlFiles.class);

    private TomlFiles() {}

    /**
     * Reads {@code path} (strict UTF-8) as TOML and returns the root table. Values are {@link
     * String}, {@link Long}, {@link Double}, {@link Boolean}, date/time objects, {@link List} and
     * {@link Map}.
     *
     * @return the root table; an empty map if {@code path} is not a regular file (silently), or if
     *     it cannot be read or is not valid TOML (logged at {@code ERROR})
     */
    static Map<String, Object> readToml(Path path) {
        if (!Files.isRegularFile(path)) {
            return Collections.emptyMap();
        }
        TOML toml;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            toml = TOMLParser.parseFromReader(reader);
        } catch (IOException | RuntimeException e) {
            logger.error("Error reading file: {}", path, e);
            return Collections.emptyMap();
        }
        if (!toml.getErrors().isEmpty()) {
            if (logger.isErrorEnabled()) {
                logger.error("Invalid TOML in {}:\n{}", path, String.join("\n", toml.getErrors()));
            }
            return Collections.emptyMap();
        }
        return toml.getRoot();
    }

    /**
     * Text of a string or number value, or {@code null} for anything else (also an empty string). A
     * number is rendered without exponent ({@code 3.0} stays {@code 3.0}); as TOML reads {@code
     * 3.10} as the float {@code 3.1}, a version that must keep its trailing zero has to be quoted.
     */
    static String scalarText(Object value) {
        if (value instanceof String) {
            return ((String) value).isEmpty() ? null : (String) value;
        }
        if (value instanceof Long) {
            return value.toString();
        }
        if (value instanceof Double
                && !((Double) value).isNaN()
                && !((Double) value).isInfinite()) {
            return BigDecimal.valueOf((Double) value).toPlainString();
        }
        return null;
    }
}

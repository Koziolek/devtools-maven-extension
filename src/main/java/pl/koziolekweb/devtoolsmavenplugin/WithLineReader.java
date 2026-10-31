package pl.koziolekweb.devtoolsmavenplugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mixin for {@link ToolParser}s that read a line-oriented version-pin file: resolves the file under
 * the reactor root and returns its lines, so each parser does not repeat the "does it exist? then
 * read it as UTF-8" boilerplate.
 *
 * <p>It only locates and reads the file. Interpreting the lines — trimming, skipping blanks and
 * {@code #} comments, splitting key from value, logging malformed lines — stays with the
 * implementing parser.
 */
interface WithLineReader {

    Logger WLR_LOGGER = LoggerFactory.getLogger(WithLineReader.class);

    /**
     * Reads {@code fileName}, resolved against {@code root}, as UTF-8 text.
     *
     * <p>The lines are returned exactly as in the file: not trimmed, and blank lines and comments
     * are kept, so the position in the list maps to the line number in the file ({@code index +
     * 1}). Line terminators ({@code \n}, {@code \r\n}, {@code \r}) are not part of the lines. The
     * whole file is read into memory, which is fine for the small config files parsed here.
     *
     * @param root the reactor root directory
     * @param fileName the file name, or a path relative to {@code root} (e.g. {@code
     *     .mvn/wrapper/maven-wrapper.properties})
     * @return the lines of the file; {@link Optional#empty()} if {@code fileName} does not exist
     *     under {@code root} or is not a regular file (e.g. a directory). An existing but empty
     *     file yields a present, empty list — this distinguishes "no file" from "file without
     *     content". A file that exists but cannot be read (invalid UTF-8, permissions) is logged at
     *     {@code ERROR} with its path and also yields {@link Optional#empty()}; this method never
     *     throws, so one bad file cannot stop the other parsers.
     */
    default Optional<List<String>> readLines(Path root, String fileName) {
        Path path = root.resolve(fileName);
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllLines(path, StandardCharsets.UTF_8));
        } catch (IOException e) {
            WLR_LOGGER.error("Error reading file: {}", path, e);
            return Optional.empty();
        }
    }

    /**
     * Reads {@code fileName} like {@link #readLines(Path, String)} and hands its lines to {@code
     * processor}, which turns them into the parser's result. This is the whole body of a typical
     * {@link ToolParser#parse(Path)}.
     *
     * <p>The {@code processor} runs only if the file exists; for a missing file (or a directory) it
     * is not called at all and an empty map is returned, which is what {@link ToolParser} requires.
     * The same holds for a file that cannot be read; that case is logged at {@code ERROR}. An
     * existing but empty file does call it, with an empty list. If the {@code processor} returns
     * {@code null} the result is also an empty map, never {@code null}.
     *
     * @param root the reactor root directory
     * @param fileName the file name, or a path relative to {@code root}
     * @param processor converts the raw lines (see {@link #readLines(Path, String)} for their exact
     *     form) to the already-prefixed properties; exceptions it throws are not caught
     * @return what {@code processor} returned; an empty map, never {@code null}, if the file does
     *     not exist or {@code processor} returned {@code null}
     */
    default Map<String, String> readAndProcessLines(
            Path root, String fileName, Function<List<String>, Map<String, String>> processor) {
        return readLines(root, fileName).map(processor).orElseGet(Collections::emptyMap);
    }
}

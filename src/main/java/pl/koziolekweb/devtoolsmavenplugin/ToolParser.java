package pl.koziolekweb.devtoolsmavenplugin;

import java.nio.file.Path;
import java.util.Map;

/**
 * Reads one dev-tool version-pin file from the reactor root and exposes its entries as already
 * namespaced properties (e.g. {@code sdkman.java}). Implementations own their file name and
 * property prefix; the caller only knows the reactor root.
 */
public interface ToolParser {

    /**
     * The namespace of every key this parser returns, with the trailing dot (e.g. {@code
     * "sdkman."}). It must be unique among parsers and match the prefix of the keys returned by
     * {@link #parse(Path)}. {@link TransformConfiguration} resolves bare transform sources against
     * these prefixes, so a new parser is picked up there without further registration.
     *
     * @return the property prefix, ending with a dot
     */
    String propertyPrefix();

    /**
     * Parses this parser's file under {@code root}. Keys are already prefixed for direct insertion
     * into Maven properties.
     *
     * @param root the reactor root directory to resolve this parser's file under
     * @return the parsed, already-prefixed entries; an empty map, never {@code null}, if the file
     *     does not exist or cannot be read (the latter is logged, not thrown)
     */
    Map<String, String> parse(Path root);
}

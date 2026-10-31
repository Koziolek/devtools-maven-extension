package pl.koziolekweb.devtoolsmavenplugin;

import java.util.Map;
import javax.inject.Named;
import javax.inject.Singleton;

/**
 * Parser for asdf's {@code .tool-versions}, following asdf's own rules: a trailing {@code # ...}
 * comment is stripped, the key is separated from the value by any run of whitespace (spaces and/or
 * tabs), and when a key is repeated the first occurrence wins. The value is everything after the
 * separator verbatim (fallback versions, {@code system}, {@code ref:...}, {@code path:...}), only
 * trimmed. A line without a key or value is logged at WARN by the base class and skipped.
 */
@Named
@Singleton
final class AsdfToolVersionsParser extends AbstractKeyValueLineParser {

    static final String FILE_NAME = ".tool-versions";
    static final String PREFIX = "asdf.";
    static final char SEPARATOR = ' ';

    AsdfToolVersionsParser() {
        super(FILE_NAME, PREFIX, SEPARATOR);
    }

    @Override
    boolean parseLine(String line, Map<String, String> values) {
        final String content = dropComment(line);

        int index = findFirstWhitespace(content);
        if (index == content.length()) {
            return false;
        }

        String key = content.substring(0, index);
        String value = content.substring(index).trim();

        values.putIfAbsent(key, value);
        return true;
    }

    private int findFirstWhitespace(String content) {
        final char[] charArray = content.trim().toCharArray();
        int i = 0;
        while (i < charArray.length) {
            if (Character.isWhitespace(charArray[i])) {
                return i;
            }
            i++;
        }
        return i;
    }

    private String dropComment(String line) {
        int hash = line.indexOf('#');
        return (hash >= 0 ? line.substring(0, hash) : line).trim();
    }
}

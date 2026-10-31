package pl.koziolekweb.devtoolsmavenplugin;

import com.squareup.moshi.JsonDataException;
import com.squareup.moshi.JsonReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import okio.Buffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared JSON / JSONC reading for the JSON-based parsers (one lenient reader for all of them). Not
 * a Sisu component. Moshi classes are only touched when a file actually exists and is read, so a
 * project without these files pays no class-loading cost.
 *
 * <p>The result is a tree of {@link Map}, {@link List}, {@link String}, {@link Boolean}, {@code
 * null} and — for numbers — {@link BigDecimal} built from the literal text, so {@code 3.10} keeps
 * its trailing zero (a {@code double} would turn it into {@code 3.1}).
 */
final class JsonFiles {
    private static final Logger logger = LoggerFactory.getLogger(JsonFiles.class);

    private JsonFiles() {}

    /**
     * Reads {@code path} (strict UTF-8) as a JSON object. Comments ({@code //}, {@code /* *}{@code
     * /}) and trailing commas are tolerated for every file, {@code package.json} included. A
     * duplicate key in an object is an error.
     *
     * <p>Nothing is thrown: an unreadable file or invalid JSON is logged at {@code ERROR} with the
     * path of the file and yields an empty map, so a parser needs no error handling of its own.
     *
     * @return the root object; an empty map if {@code path} is not a regular file (silently), if
     *     the document root is not an object (silently), or on a read or syntax error (logged)
     */
    static Map<Object, Object> readJson(Path path) {
        if (!Files.isRegularFile(path)) {
            return Collections.emptyMap();
        }
        try (Buffer buffer = new Buffer()) {
            buffer.writeUtf8(stripCommentsAndTrailingCommas(readStrictUtf8(path)));
            final JsonReader jsonReader = JsonReader.of(buffer);
            final Object document = readValue(jsonReader);
            if (document instanceof Map) {
                return (Map<Object, Object>) document;
            }
            return Collections.emptyMap();

        } catch (IOException | RuntimeException e) {
            logger.error("Error reading file: {}", path, e);
            return Collections.emptyMap();
        }
    }

    /**
     * The content of {@code path} as text. Unlike {@code new String(bytes, UTF_8)}, which silently
     * replaces malformed bytes with {@code U+FFFD}, the decoder reports them, so a file that is not
     * valid UTF-8 fails with a {@link java.nio.charset.CharacterCodingException} (an {@link
     * IOException}), like in {@link WithLineReader} and {@link TomlFiles}.
     */
    private static String readStrictUtf8(Path path) throws IOException {
        return StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(Files.readAllBytes(path)))
                .toString();
    }

    /**
     * The object stored under {@code sectionName} in the root object of {@code path}, see {@link
     * #readJson(Path)}.
     *
     * @return the section; an empty map if the file is missing or invalid, or the section is absent
     *     or not an object
     */
    static Map<Object, Object> readJsonSection(Path path, String sectionName) {
        Object section = readJson(path).get(sectionName);
        if (!(section instanceof Map)) {
            return Collections.emptyMap();
        }
        return (Map<Object, Object>) section;
    }

    /**
     * Renders a JSON number without exponent and without losing the written precision ({@code 3.10}
     * stays {@code 3.10}); an integral value is rendered without a fraction ({@code 20.0} gives
     * {@code 20}). {@code null} if {@code value} is not a number.
     */
    static String numberText(Object value) {
        if (!(value instanceof BigDecimal)) {
            return null;
        }
        BigDecimal number = (BigDecimal) value;
        if (number.signum() == 0 || number.stripTrailingZeros().scale() <= 0) {
            return number.toBigInteger().toString();
        }
        return number.toPlainString();
    }

    private static Object readValue(JsonReader reader) throws IOException {
        // spotless:off (checkstyle: caseIndent 2)
        switch (reader.peek()) {
          case BEGIN_OBJECT:
              Map<String, Object> map = new LinkedHashMap<>();
              reader.beginObject();
              while (reader.hasNext()) {
                  String name = reader.nextName();
                  if (map.put(name, readValue(reader)) != null) {
                      throw new JsonDataException("Map key '" + name + "' has multiple values");
                  }
              }
              reader.endObject();
              return map;
          case BEGIN_ARRAY:
              List<Object> list = new ArrayList<>();
              reader.beginArray();
              while (reader.hasNext()) {
                  list.add(readValue(reader));
              }
              reader.endArray();
              return list;
          case STRING:
              return reader.nextString();
          case NUMBER:
              return new BigDecimal(reader.nextString());
          case BOOLEAN:
              return reader.nextBoolean();
          default:
              return reader.nextNull();
        }
        // spotless:on
    }

    /**
     * Removes {@code //} and {@code /* *}{@code /} comments and commas directly preceding a closing
     * brace or bracket, leaving string literals untouched.
     */
    static String stripCommentsAndTrailingCommas(String input) {
        return new CommentStripper(input).strip();
    }

    /** Single-use scanner behind {@link #stripCommentsAndTrailingCommas(String)}. */
    private static final class CommentStripper {
        private final String input;
        private final int length;
        private final StringBuilder out;
        private int pos;
        private int pendingComma = -1;

        CommentStripper(String input) {
            this.input = input;
            this.length = input.length();
            this.out = new StringBuilder(length);
        }

        String strip() {
            while (pos < length) {
                char c = input.charAt(pos);
                if (c == '"') {
                    copyString();
                } else if (startsWith("//")) {
                    skipLineComment();
                } else if (startsWith("/*")) {
                    skipBlockComment();
                } else {
                    copyPlain(c);
                }
            }
            return out.toString();
        }

        private boolean startsWith(String token) {
            return input.startsWith(token, pos);
        }

        private void copyString() {
            pendingComma = -1;
            out.append(input.charAt(pos++));
            while (pos < length) {
                char s = input.charAt(pos++);
                out.append(s);
                if (s == '\\' && pos < length) {
                    out.append(input.charAt(pos++));
                } else if (s == '"') {
                    return;
                }
            }
        }

        private void skipLineComment() {
            pos += 2;
            while (pos < length && input.charAt(pos) != '\n' && input.charAt(pos) != '\r') {
                pos++;
            }
        }

        private void skipBlockComment() {
            int end = input.indexOf("*/", pos + 2);
            pos = end < 0 ? length : end + 2;
            out.append(' ');
        }

        private void copyPlain(char c) {
            if (c == '}' || c == ']') {
                if (pendingComma >= 0) {
                    out.setCharAt(pendingComma, ' ');
                }
                pendingComma = -1;
            } else if (c == ',') {
                pendingComma = out.length();
            } else if (!Character.isWhitespace(c)) {
                pendingComma = -1;
            }
            out.append(c);
            pos++;
        }
    }
}

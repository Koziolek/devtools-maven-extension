package pl.koziolekweb.devtoolsmavenplugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class JsonFilesTest {

    @TempDir Path tempDir;

    private Path write(String content) throws Exception {
        Path file = tempDir.resolve("test.json");
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private Map<Object, Object> read(String content) throws Exception {
        return JsonFiles.readJson(write(content));
    }

    private static Map<Object, Object> mapOf(Object key, Object value) {
        return Collections.singletonMap(key, value);
    }

    // --- readJson: result tree ---

    @Test
    void readsNestedObjectsArraysAndScalars() throws Exception {
        Map<Object, Object> root =
                read(
                        "{\"s\":\"text\",\"b\":true,\"n\":null,\"list\":[1,\"two\",false],"
                                + "\"obj\":{\"k\":\"v\"}}");

        assertEquals("text", root.get("s"));
        assertEquals(Boolean.TRUE, root.get("b"));
        assertTrue(root.containsKey("n"));
        assertNull(root.get("n"));
        assertEquals(Arrays.<Object>asList(new BigDecimal("1"), "two", false), root.get("list"));
        assertEquals(mapOf("k", "v"), root.get("obj"));
    }

    @Test
    void keepsKeyOrder() throws Exception {
        Map<Object, Object> root = read("{\"z\":1,\"a\":2,\"m\":3}");

        assertEquals(Arrays.asList("z", "a", "m"), new ArrayList<>(root.keySet()));
    }

    @Test
    void numbersKeepTheirLiteralText() throws Exception {
        Map<Object, Object> root = read("{\"a\":3.10,\"b\":20,\"c\":1e3,\"d\":-0.50}");

        assertEquals(new BigDecimal("3.10"), root.get("a"));
        assertEquals(new BigDecimal("20"), root.get("b"));
        assertEquals(new BigDecimal("1e3"), root.get("c"));
        assertEquals(new BigDecimal("-0.50"), root.get("d"));
    }

    @Test
    void readsUnicodeEscapesAndNonAsciiText() throws Exception {
        Map<Object, Object> root = read("{\"a\":\"\\u0142\\u00f3d\\u017a\",\"b\":\"żółw\"}");

        assertEquals("łódź", root.get("a"));
        assertEquals("żółw", root.get("b"));
    }

    // --- readJson: comments and trailing commas ---

    @Test
    void toleratesLineAndBlockComments() throws Exception {
        Map<Object, Object> root =
                read(
                        "// header\n{\n  /* block\n spanning lines */ \"a\": 1, // tail\n"
                                + "  \"b\": /* inline */ 2\n}\n// footer");

        assertEquals(new BigDecimal("1"), root.get("a"));
        assertEquals(new BigDecimal("2"), root.get("b"));
        assertEquals(2, root.size());
    }

    @Test
    void toleratesTrailingCommas() throws Exception {
        Map<Object, Object> root = read("{\"list\":[1,2,],\"obj\":{\"k\":\"v\",},}");

        assertEquals(
                Arrays.<Object>asList(new BigDecimal("1"), new BigDecimal("2")), root.get("list"));
        assertEquals(mapOf("k", "v"), root.get("obj"));
    }

    @Test
    void commentBetweenTrailingCommaAndClosingBraceIsFine() throws Exception {
        Map<Object, Object> root = read("{\"a\": 1, // last\n /* none */ }");

        assertEquals(mapOf("a", new BigDecimal("1")), root);
    }

    @Test
    void commentMarkersInsideStringsAreData() throws Exception {
        Map<Object, Object> root =
                read(
                        "{\"url\":\"http://example.com/*x*/\",\"c\":\"a, ]\",\"q\":\"say \\\"//hi\\\"\"}");

        assertEquals("http://example.com/*x*/", root.get("url"));
        assertEquals("a, ]", root.get("c"));
        assertEquals("say \"//hi\"", root.get("q"));
    }

    @Test
    void unterminatedBlockCommentSwallowsTheRest() throws Exception {
        assertEquals(mapOf("a", new BigDecimal("1")), read("{\"a\":1} /* never closed"));
    }

    // --- readJson: silent empty results ---

    @Test
    void missingFileGivesEmptyMapWithoutLogging() {
        try (LogCapture log = new LogCapture()) {
            assertTrue(JsonFiles.readJson(tempDir.resolve("nope.json")).isEmpty());
            assertEquals("", log.output());
        }
    }

    @Test
    void directoryGivesEmptyMapWithoutLogging() {
        try (LogCapture log = new LogCapture()) {
            assertTrue(JsonFiles.readJson(tempDir).isEmpty());
            assertEquals("", log.output());
        }
    }

    @ParameterizedTest
    @CsvSource({"'[1,2]'", "'\"text\"'", "'42'", "'true'", "'null'"})
    void rootThatIsNotAnObjectGivesEmptyMapWithoutLogging(String json) throws Exception {
        try (LogCapture log = new LogCapture()) {
            assertTrue(read(json).isEmpty());
            assertEquals("", log.output());
        }
    }

    // --- readJson: errors are logged, never thrown ---

    @ParameterizedTest
    @CsvSource({
        "'{\"a\":'",
        "'{\"a\" 1}'",
        "'{a:1}'",
        "'{\"a\":1'",
        "'{\"a\":01x}'",
        "''",
        "'   '"
    })
    void invalidJsonIsLoggedAtErrorWithThePath(String json) throws Exception {
        Path file = write(json);
        try (LogCapture log = new LogCapture()) {
            assertTrue(JsonFiles.readJson(file).isEmpty());

            String out = log.output();
            assertTrue(out.contains("ERROR"), out);
            assertTrue(out.contains("Error reading file: " + file), out);
        }
    }

    @Test
    void invalidUtf8IsLoggedAtErrorAndNotReplacedSilently() throws Exception {
        Path file = tempDir.resolve("bad.json");
        Files.write(file, new byte[] {'{', '"', 'a', '"', ':', '"', (byte) 0xC3, 0x28, '"', '}'});
        try (LogCapture log = new LogCapture()) {
            assertTrue(JsonFiles.readJson(file).isEmpty());

            String out = log.output();
            assertTrue(out.contains("Error reading file: " + file), out);
            assertTrue(out.contains("MalformedInputException"), out);
        }
    }

    @Test
    void validMultiByteUtf8IsReadAsIs() throws Exception {
        Path file = tempDir.resolve("ok.json");
        Files.write(file, "{\"a\":\"é€😀\"}".getBytes(StandardCharsets.UTF_8));

        assertEquals("é€😀", JsonFiles.readJson(file).get("a"));
    }

    @Test
    void duplicateKeyInvalidatesTheWholeFile() throws Exception {
        Path file = write("{\"a\":1,\"b\":2,\"a\":3}");
        try (LogCapture log = new LogCapture()) {
            assertTrue(JsonFiles.readJson(file).isEmpty());
            assertTrue(log.output().contains("multiple values"), log.output());
        }
    }

    @Test
    void duplicateKeyInNestedObjectInvalidatesTheWholeFile() throws Exception {
        Path file = write("{\"ok\":1,\"o\":{\"k\":1,\"k\":2}}");
        try (LogCapture log = new LogCapture()) {
            assertTrue(JsonFiles.readJson(file).isEmpty());
            assertTrue(log.output().contains("Error reading file: " + file), log.output());
        }
    }

    @Test
    void sameKeyInDifferentObjectsIsFine() throws Exception {
        Map<Object, Object> root = read("{\"a\":{\"k\":1},\"b\":{\"k\":2}}");

        assertEquals(2, root.size());
    }

    // --- readJsonSection ---

    @Test
    void sectionIsReturnedWhenItIsAnObject() throws Exception {
        Path file = write("{\"volta\":{\"node\":\"20\"},\"other\":1}");

        assertEquals(mapOf("node", "20"), JsonFiles.readJsonSection(file, "volta"));
    }

    @Test
    void sectionIsEmptyWhenAbsentOrNotAnObject() throws Exception {
        Path file = write("{\"list\":[1],\"text\":\"x\",\"n\":null}");

        assertTrue(JsonFiles.readJsonSection(file, "missing").isEmpty());
        assertTrue(JsonFiles.readJsonSection(file, "list").isEmpty());
        assertTrue(JsonFiles.readJsonSection(file, "text").isEmpty());
        assertTrue(JsonFiles.readJsonSection(file, "n").isEmpty());
    }

    @Test
    void sectionIsEmptyForMissingOrInvalidFile() throws Exception {
        try (LogCapture log = new LogCapture()) {
            assertTrue(JsonFiles.readJsonSection(tempDir.resolve("nope.json"), "a").isEmpty());
            assertEquals("", log.output());

            assertTrue(JsonFiles.readJsonSection(write("{\"a\":"), "a").isEmpty());
            assertTrue(log.output().contains("ERROR"), log.output());
        }
    }

    // --- numberText ---

    @ParameterizedTest
    @CsvSource({
        "3.10, 3.10",
        "3.1, 3.1",
        "20, 20",
        "20.0, 20",
        "20.00, 20",
        "0, 0",
        "0.0, 0",
        "100, 100",
        "1E+3, 1000",
        "1.5E-3, 0.0015",
        "-2.50, -2.50",
        "-7.0, -7"
    })
    void numberTextRendersWithoutExponentAndKeepsPrecision(String literal, String expected) {
        assertEquals(expected, JsonFiles.numberText(new BigDecimal(literal)));
    }

    @Test
    void numberTextIsNullForNonNumbers() {
        assertNull(JsonFiles.numberText(null));
        assertNull(JsonFiles.numberText("3"));
        assertNull(JsonFiles.numberText(3));
        assertNull(JsonFiles.numberText(3.5d));
        assertNull(JsonFiles.numberText(Boolean.TRUE));
    }

    // --- stripCommentsAndTrailingCommas ---

    @Test
    void stripLeavesPlainJsonUntouched() {
        String json = "{\"a\": [1, 2, {\"b\": null}], \"c\": \"d\"}";

        assertEquals(json, JsonFiles.stripCommentsAndTrailingCommas(json));
    }

    @Test
    void stripRemovesLineCommentButKeepsTheLineBreak() {
        assertEquals("a \nb", JsonFiles.stripCommentsAndTrailingCommas("a // c\nb"));
        assertEquals("a \r\nb", JsonFiles.stripCommentsAndTrailingCommas("a // c\r\nb"));
        assertEquals("a ", JsonFiles.stripCommentsAndTrailingCommas("a // no newline at end"));
    }

    @Test
    void stripReplacesBlockCommentWithSpace() {
        assertEquals("a b", JsonFiles.stripCommentsAndTrailingCommas("a/* x */b"));
        assertEquals("a b", JsonFiles.stripCommentsAndTrailingCommas("a/* x\ny */b"));
        assertEquals("a ", JsonFiles.stripCommentsAndTrailingCommas("a/* open"));
    }

    @Test
    void stripBlanksTrailingCommaInsteadOfRemovingIt() {
        assertEquals("[1,2 ]", JsonFiles.stripCommentsAndTrailingCommas("[1,2,]"));
        assertEquals("{\"a\":1 }", JsonFiles.stripCommentsAndTrailingCommas("{\"a\":1,}"));
        assertEquals("[1 \n  ]", JsonFiles.stripCommentsAndTrailingCommas("[1,\n  ]"));
    }

    @Test
    void stripKeepsCommasThatAreNotTrailing() {
        assertEquals("[1,2,3]", JsonFiles.stripCommentsAndTrailingCommas("[1,2,3]"));
        assertEquals("[[1],[2]]", JsonFiles.stripCommentsAndTrailingCommas("[[1],[2]]"));
    }

    @Test
    void stripSeesThroughCommentBetweenCommaAndClosingBracket() {
        assertEquals("[1  \n]", JsonFiles.stripCommentsAndTrailingCommas("[1, // c\n]"));
        assertEquals("[1    ]", JsonFiles.stripCommentsAndTrailingCommas("[1, /* c */ ]"));
    }

    @Test
    void stripDoesNotTouchStrings() {
        assertEquals(
                "\"// not a comment\"",
                JsonFiles.stripCommentsAndTrailingCommas("\"// not a comment\""));
        assertEquals(
                "\"/* nor this */\"",
                JsonFiles.stripCommentsAndTrailingCommas("\"/* nor this */\""));
        assertEquals("[\"a,\"]", JsonFiles.stripCommentsAndTrailingCommas("[\"a,\"]"));
    }

    @Test
    void stripHandlesEscapedQuotesAndBackslashesInStrings() {
        String escapedQuote = "\"a\\\"// b\" // real comment";
        assertEquals("\"a\\\"// b\" ", JsonFiles.stripCommentsAndTrailingCommas(escapedQuote));

        // the backslash is escaped, so the quote ends the string and the comment starts
        String escapedBackslash = "\"a\\\\\" // real comment";
        assertEquals("\"a\\\\\" ", JsonFiles.stripCommentsAndTrailingCommas(escapedBackslash));
    }

    @Test
    void stripCopiesAnUnterminatedStringAsIs() {
        assertEquals("\"abc // x", JsonFiles.stripCommentsAndTrailingCommas("\"abc // x"));
        assertEquals("\"abc\\", JsonFiles.stripCommentsAndTrailingCommas("\"abc\\"));
    }

    @Test
    void stripHandlesEmptyInputAndLoneSlash() {
        assertEquals("", JsonFiles.stripCommentsAndTrailingCommas(""));
        assertEquals("/", JsonFiles.stripCommentsAndTrailingCommas("/"));
        assertEquals("a/b", JsonFiles.stripCommentsAndTrailingCommas("a/b"));
        assertFalse(JsonFiles.stripCommentsAndTrailingCommas("/*").contains("*"));
    }

    @Test
    void fileThatCannotBeOpenedIsLoggedAtErrorWithTheException() throws Exception {
        Path file = write("{\"a\":1}");
        Files.setPosixFilePermissions(file, Collections.<PosixFilePermission>emptySet());
        try (LogCapture log = new LogCapture()) {
            Assumptions.assumeFalse(Files.isReadable(file), "root or non-POSIX FS reads it anyway");

            assertTrue(JsonFiles.readJson(file).isEmpty());

            String out = log.output();
            assertTrue(out.contains("Error reading file: " + file), out);
            assertTrue(out.contains("AccessDeniedException"), out);
        } finally {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        }
    }
}

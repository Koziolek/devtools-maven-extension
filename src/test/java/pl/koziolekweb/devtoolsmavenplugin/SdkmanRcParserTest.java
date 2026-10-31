package pl.koziolekweb.devtoolsmavenplugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SdkmanRcParserTest {

    @TempDir Path tempDir;

    private final SdkmanRcParser parser = new SdkmanRcParser();

    @Test
    void parsesSdkmanRc() throws Exception {
        Files.write(
                tempDir.resolve(".sdkmanrc"),
                ("java=25-open\nquarkus=3.39.4\n").getBytes(StandardCharsets.UTF_8));

        Map<String, String> values = parser.parse(tempDir);

        assertEquals("25-open", values.get("sdkman.java"));
        assertEquals("3.39.4", values.get("sdkman.quarkus"));
    }

    @Test
    void returnsEmptyMapWhenFileMissing() {
        assertTrue(parser.parse(tempDir).isEmpty());
    }

    @Test
    void skipsMalformedLineInsteadOfFailing() throws Exception {
        Files.write(
                tempDir.resolve(".sdkmanrc"),
                ("java=25-open\nnot-a-valid-line\n=empty-key\nquarkus=3.39.4\n")
                        .getBytes(StandardCharsets.UTF_8));

        Map<String, String> values = parser.parse(tempDir);

        assertEquals("25-open", values.get("sdkman.java"));
        assertEquals("3.39.4", values.get("sdkman.quarkus"));
        assertEquals(2, values.size());
    }

    @Test
    void rejectsEmptyValueLikeSdkmanCliDoes() throws Exception {
        // sdkman-cli itself requires a non-empty value after '=' (see README "Malformed
        // lines").
        Files.write(
                tempDir.resolve(".sdkmanrc"),
                ("java=\nquarkus=3.39.4\n").getBytes(StandardCharsets.UTF_8));

        Map<String, String> values = parser.parse(tempDir);

        assertEquals("3.39.4", values.get("sdkman.quarkus"));
        assertEquals(1, values.size());
    }

    @Test
    void lastDuplicateKeyWins() throws Exception {
        Files.write(
                tempDir.resolve(".sdkmanrc"),
                ("java=17-open\njava=25-open\n").getBytes(StandardCharsets.UTF_8));

        Map<String, String> values = parser.parse(tempDir);

        assertEquals("25-open", values.get("sdkman.java"));
        assertEquals(1, values.size());
    }

    @Test
    void keepsEverythingAfterFirstSeparator() throws Exception {
        Files.write(
                tempDir.resolve(".sdkmanrc"),
                ("java=25-open=extra\n").getBytes(StandardCharsets.UTF_8));

        Map<String, String> values = parser.parse(tempDir);

        assertEquals("25-open=extra", values.get("sdkman.java"));
    }

    @Test
    void trimsWhitespaceAroundKeyAndValue() throws Exception {
        Files.write(
                tempDir.resolve(".sdkmanrc"),
                ("java = 25-open \n").getBytes(StandardCharsets.UTF_8));

        Map<String, String> values = parser.parse(tempDir);

        assertEquals("25-open", values.get("sdkman.java"));
    }

    @Test
    void parseLineRejectsAnEmptyKeyOrValueAfterTrimming() {
        Map<String, String> values = new java.util.LinkedHashMap<>();

        assertTrue(!parser.parseLine(" =value", values));
        assertTrue(!parser.parseLine("=value", values));
        assertTrue(!parser.parseLine("key=", values));
        assertTrue(!parser.parseLine("key=   ", values));
        assertTrue(!parser.parseLine("no-separator", values));
        assertTrue(values.isEmpty());

        assertTrue(parser.parseLine("key = value", values));
        assertEquals("value", values.get("key"));
    }
}

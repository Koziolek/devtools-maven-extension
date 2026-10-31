package pl.koziolekweb.devtoolsmavenplugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DevboxParserTest {

    @TempDir Path tempDir;

    private final DevboxParser parser = new DevboxParser();

    private void write(String json) throws Exception {
        Files.write(tempDir.resolve("devbox.json"), json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void parsesArrayForm() throws Exception {
        write("{\"packages\": [\"jdk@21\", \"maven@3.9\", \"nodejs@latest\", \"ripgrep\"]}");

        Map<String, String> values = parser.parse(tempDir);

        assertEquals("21", values.get("devbox.jdk"));
        assertEquals("3.9", values.get("devbox.maven"));
        assertEquals("latest", values.get("devbox.nodejs"));
        assertEquals(3, values.size());
    }

    @Test
    void parsesObjectForm() throws Exception {
        write(
                "{\"packages\": {\"jdk\": \"21\", \"maven\": {\"version\": \"3.9\"},"
                        + " \"nodejs\": \"latest\", \"go\": {\"platforms\": [\"x\"]}}}");

        Map<String, String> values = parser.parse(tempDir);

        assertEquals("21", values.get("devbox.jdk"));
        assertEquals("3.9", values.get("devbox.maven"));
        assertEquals("latest", values.get("devbox.nodejs"));
        assertEquals(3, values.size());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"packages\": [\"ripgrep\", \"jdk@21\"]}",
                "{\"packages\": [\"github:F1bonacc1/process-compose/v1.2@x\","
                        + " \"path:./flake#hello@1\", \"jdk@21\"]}",
                "{\"packages\": [1, null, true, [\"x@1\"], {\"a\": 1}, \"jdk@21\"]}",
                "{\"packages\": [\"maven@\", \"nodejs@ \", \"jdk@21\"]}"
            })
    void skipsUnusableArrayEntriesAndKeepsTheVersionedOne(String json) throws Exception {
        write(json);

        Map<String, String> values = parser.parse(tempDir);

        assertEquals(1, values.size());
        assertEquals("21", values.get("devbox.jdk"));
    }

    @Test
    void returnsEmptyMapOnInvalidJson() throws Exception {
        write("{ not json");

        assertTrue(parser.parse(tempDir).isEmpty());
    }

    @Test
    void returnsEmptyMapWhenFileMissing() {
        assertTrue(parser.parse(tempDir).isEmpty());
    }

    @Test
    void returnsEmptyMapWhenPackagesHasWrongType() throws Exception {
        write("{\"packages\": \"jdk@21\"}");
        assertTrue(parser.parse(tempDir).isEmpty());

        write("{\"shell\": {}}");
        assertTrue(parser.parse(tempDir).isEmpty());
    }

    @Test
    void toleratesCommentsAndTrailingCommas() throws Exception {
        write(
                "// header\n"
                        + "{\n"
                        + "  /* block\n comment */\n"
                        + "  \"packages\": [\n"
                        + "    \"jdk@21\", // line comment\n"
                        + "    \"maven@3.9\",\n"
                        + "  ],\n"
                        + "}\n");

        Map<String, String> values = parser.parse(tempDir);

        assertEquals("21", values.get("devbox.jdk"));
        assertEquals("3.9", values.get("devbox.maven"));
        assertEquals(2, values.size());
    }

    @Test
    void skipsObjectEntriesWithAnEmptyNameOrVersion() throws Exception {
        write(
                "{\"packages\": {\"\": \"1\", \"  \": \"2\", \"jdk\": \"  \","
                        + " \"maven\": {\"version\": \"\"}, \"nodejs\": \"20\"}}");

        Map<String, String> values = parser.parse(tempDir);

        assertEquals(1, values.size());
        assertEquals("20", values.get("devbox.nodejs"));
    }
}

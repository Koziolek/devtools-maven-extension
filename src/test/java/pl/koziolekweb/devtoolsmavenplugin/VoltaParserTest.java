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
import org.junit.jupiter.params.provider.MethodSource;

class VoltaParserTest {

    private final VoltaParser parser = new VoltaParser();
    @TempDir Path tempDir;

    private void write(String json) throws Exception {
        Files.write(tempDir.resolve("package.json"), json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void parsesNodeNpmAndYarn() throws Exception {
        write(
                "{\"name\":\"x\",\"volta\":{\"node\":\"20.11.0\",\"npm\":\"10.2.4\","
                        + "\"yarn\":\"1.22.19\"}}");

        Map<String, String> values = parser.parse(tempDir);

        assertEquals(3, values.size());
        assertEquals("20.11.0", values.get("volta.node"));
        assertEquals("10.2.4", values.get("volta.npm"));
        assertEquals("1.22.19", values.get("volta.yarn"));
    }

    @ParameterizedTest
    @MethodSource("pl.koziolekweb.devtoolsmavenplugin.ParserFixtures#voltaExpectedEmpty")
    void returnsEmptyMapWithoutVoltaSection(String fileContent) throws Exception {
        write(fileContent);
        assertTrue(parser.parse(tempDir).isEmpty());
    }

    @Test
    void ignoresNonStringValues() throws Exception {
        write("{\"volta\":{\"node\":\"20.11.0\",\"npm\":10,\"yarn\":null,\"pnpm\":{\"a\":1}}}");

        Map<String, String> values = parser.parse(tempDir);

        assertEquals(1, values.size());
        assertEquals("20.11.0", values.get("volta.node"));
    }

    @Test
    void ignoresExtendsKey() throws Exception {
        write("{\"volta\":{\"extends\":\"../package.json\",\"node\":\"20.11.0\"}}");

        Map<String, String> values = parser.parse(tempDir);

        assertEquals(1, values.size());
        assertEquals("20.11.0", values.get("volta.node"));
    }

    @Test
    void returnsEmptyMapWhenFileMissing() {
        assertTrue(parser.parse(tempDir).isEmpty());
    }

    @Test
    void skipsEntriesWithAnEmptyNameOrBlankValue() throws Exception {
        write(
                "{\"volta\": {\"\": \"1\", \"  \": \"2\", \"yarn\": \"  \","
                        + " \"npm\": \"\", \"node\": \" 20.11.0 \"}}");

        Map<String, String> values = parser.parse(tempDir);

        assertEquals(1, values.size());
        assertEquals("20.11.0", values.get("volta.node"));
    }
}

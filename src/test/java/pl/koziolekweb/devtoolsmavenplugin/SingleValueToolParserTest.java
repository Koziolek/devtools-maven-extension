package pl.koziolekweb.devtoolsmavenplugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SingleValueToolParserTest {

    private static final class TestParser extends SingleValueToolParser {
        TestParser() {
            super(".test-version", "test.", "tool");
        }
    }

    @TempDir Path tempDir;

    private final TestParser parser = new TestParser();

    private void write(String content) throws Exception {
        Files.write(tempDir.resolve(".test-version"), content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void parsesSingleValue() throws Exception {
        write("21.0.2\n");

        Map<String, String> values = parser.parse(tempDir);

        assertEquals("21.0.2", values.get("test.tool"));
        assertEquals(1, values.size());
    }

    @Test
    void returnsEmptyMapWhenFileMissing() {
        assertTrue(parser.parse(tempDir).isEmpty());
    }

    @Test
    void returnsEmptyMapWhenFileEmpty() throws Exception {
        write("");

        assertTrue(parser.parse(tempDir).isEmpty());
    }

    @Test
    void returnsEmptyMapWhenOnlyBlankLinesAndComments() throws Exception {
        write("\n   \n# just a comment\n");

        assertTrue(parser.parse(tempDir).isEmpty());
    }

    @Test
    void skipsLeadingBlankLinesAndComments() throws Exception {
        write("\n# pinned by CI\n\n21.0.2\n");

        assertEquals("21.0.2", parser.parse(tempDir).get("test.tool"));
    }

    @Test
    void ignoresLinesAfterTheFirstValue() throws Exception {
        write("21.0.2\n17.0.1\n");

        Map<String, String> values = parser.parse(tempDir);

        assertEquals("21.0.2", values.get("test.tool"));
        assertEquals(1, values.size());
    }

    @Test
    void trimsWhitespaceAndHandlesCrLf() throws Exception {
        write("  21.0.2 \r\n");

        assertEquals("21.0.2", parser.parse(tempDir).get("test.tool"));
    }

    @Test
    void keepsValueVerbatim() throws Exception {
        write("v20.11.0\n");

        assertEquals("v20.11.0", parser.parse(tempDir).get("test.tool"));
    }

    @Test
    void returnsEmptyMapWhenPathIsDirectory() throws Exception {
        Files.createDirectory(tempDir.resolve(".test-version"));

        assertTrue(parser.parse(tempDir).isEmpty());
    }
}

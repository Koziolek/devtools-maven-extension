package pl.koziolekweb.devtoolsmavenplugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NvmParserTest {

    @TempDir Path tempDir;

    private final NvmParser parser = new NvmParser();

    private void write(String name, String content) throws Exception {
        Files.write(tempDir.resolve(name), content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void parsesVersionFile() throws Exception {
        write(".nvmrc", "# pin\n\n1.2.3\n");

        Map<String, String> values = parser.parse(tempDir);

        assertEquals("1.2.3", values.get("nvm.node"));
        assertEquals(1, values.size());
    }

    @Test
    void keepsLeadingV() throws Exception {
        write(".nvmrc", "v20.11.0\n");

        assertEquals("v20.11.0", parser.parse(tempDir).get("nvm.node"));
    }

    @Test
    void returnsEmptyMapWhenNoFileExists() {
        assertTrue(parser.parse(tempDir).isEmpty());
    }

    @Test
    void usesNodeVersionWhenOnlyItExists() throws Exception {
        write(".node-version", "# pin\n\n1.2.3\n");

        Map<String, String> values = parser.parse(tempDir);

        assertEquals("1.2.3", values.get("nvm.node"));
        assertEquals(1, values.size());
    }

    @Test
    void fallsBackToNodeVersionWhenNvmrcHasNoValue() throws Exception {
        write(".nvmrc", "# nothing\n");
        write(".node-version", "1.2.3\n");

        assertEquals("1.2.3", parser.parse(tempDir).get("nvm.node"));
    }

    @Test
    void nvmrcWinsAndWarnsWhenValuesDiffer() throws Exception {
        write(".nvmrc", "v20.11.0\n");
        write(".node-version", "20.10.0\n");

        try (LogCapture log = new LogCapture()) {
            Map<String, String> values = parser.parse(tempDir);

            assertEquals("v20.11.0", values.get("nvm.node"));
            assertEquals(1, values.size());
            String out = log.output();
            assertTrue(out.contains("WARN"), out);
            assertTrue(out.contains(".nvmrc") && out.contains(".node-version"), out);
            assertTrue(out.contains("v20.11.0") && out.contains("20.10.0"), out);
        }
    }

    @Test
    void noWarnWhenBothFilesAgree() throws Exception {
        write(".nvmrc", "20.11.0\n");
        write(".node-version", "20.11.0\n");

        try (LogCapture log = new LogCapture()) {
            assertEquals("20.11.0", parser.parse(tempDir).get("nvm.node"));
            assertFalse(log.output().contains("WARN"), log.output());
        }
    }
}

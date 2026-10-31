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

class MiseParserTest {

    @TempDir Path tempDir;

    private final MiseParser parser = new MiseParser();

    private void write(String name, String content) throws Exception {
        Files.write(tempDir.resolve(name), content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void parsesQuotedAndUnquotedValues() throws Exception {
        write(
                "mise.toml",
                "[tools]\n" + "java = \"temurin-21\"\n" + "node = '20.1.0'\n" + "python = 3.12\n");

        Map<String, String> values = parser.parse(tempDir);

        assertEquals("temurin-21", values.get("mise.java"));
        assertEquals("20.1.0", values.get("mise.node"));
        assertEquals("3.12", values.get("mise.python"));
        assertEquals(3, values.size());
    }

    @Test
    void returnsEmptyMapWhenFilesMissing() {
        assertTrue(parser.parse(tempDir).isEmpty());
    }

    @Test
    void handlesCommentsBlankLinesAndInlineComments() throws Exception {
        write(
                "mise.toml",
                "# top\n\n[tools]\n# note\njava = \"21\" # inline\n\nnode = 20 # inline too\n");

        Map<String, String> values = parser.parse(tempDir);

        assertEquals("21", values.get("mise.java"));
        assertEquals("20", values.get("mise.node"));
        assertEquals(2, values.size());
    }

    @Test
    void takesFirstElementOfArray() throws Exception {
        write("mise.toml", "[tools]\njava = [\"temurin-21\", \"17\"]\nnode = ['18']\n");

        Map<String, String> values = parser.parse(tempDir);

        assertEquals("temurin-21", values.get("mise.java"));
        assertEquals("18", values.get("mise.node"));
    }

    @Test
    void takesVersionFromInlineTable() throws Exception {
        write(
                "mise.toml",
                "[tools]\n"
                        + "java = { version = \"21\" }\n"
                        + "node = { os = [\"linux\"], version = '20' }\n");

        Map<String, String> values = parser.parse(tempDir);

        assertEquals("21", values.get("mise.java"));
        assertEquals("20", values.get("mise.node"));
    }

    @Test
    void skipsInlineTableWithoutVersion() throws Exception {
        write("mise.toml", "[tools]\njava = { os = \"linux\" }\nnode = \"20\"\n");

        Map<String, String> values = parser.parse(tempDir);

        assertFalse(values.containsKey("mise.java"));
        assertEquals("20", values.get("mise.node"));
        assertEquals(1, values.size());
    }

    @Test
    void keepsTextInsideQuotedKeys() throws Exception {
        write("mise.toml", "[tools]\n\"npm:foo\" = \"1.2.3\"\n'npm:bar' = \"2\"\n");

        Map<String, String> values = parser.parse(tempDir);

        assertEquals("1.2.3", values.get("mise.npm:foo"));
        assertEquals("2", values.get("mise.npm:bar"));
    }

    @Test
    void ignoresOtherSections() throws Exception {
        write(
                "mise.toml",
                "[env]\nFOO = \"bar\"\n[tools]\njava = \"21\"\n[tasks]\nbuild = \"x\"\n");

        Map<String, String> values = parser.parse(tempDir);

        assertEquals(1, values.size());
        assertEquals("21", values.get("mise.java"));
    }

    @Test
    void sectionHeaderIsWhitespaceTolerantButExact() throws Exception {
        write(
                "mise.toml",
                "  [ tools ]  # c\njava = \"21\"\n[tools.foo]\nx = \"1\"\n[toolsx]\ny = \"2\"\n");

        Map<String, String> values = parser.parse(tempDir);

        assertEquals(1, values.size());
        assertEquals("21", values.get("mise.java"));
    }

    @Test
    void ignoresWholeFileWithOneErrorOnSyntaxError() throws Exception {
        write(
                "mise.toml",
                "[tools]\njava = \"21\"\nnot-valid\nnode = \"20\"\nbad = \"unterminated\n");

        try (LogCapture log = new LogCapture()) {
            assertTrue(parser.parse(tempDir).isEmpty());

            String out = log.output();
            assertTrue(out.contains("Invalid TOML in "), out);
            assertTrue(out.contains("/mise.toml"), out);
            assertEquals(1, out.split("ERROR", -1).length - 1, out);
        }
    }

    @Test
    void invalidFileDoesNotHideTheOtherOne() throws Exception {
        write("mise.toml", "[tools]\njava = \n");
        write(".mise.toml", "[tools]\njava = \"17\"\n");

        try (LogCapture log = new LogCapture()) {
            assertEquals("17", parser.parse(tempDir).get("mise.java"));
            // "/mise.toml" with the slash: ".mise.toml" is the valid one and must not be named.
            assertTrue(log.output().contains("Invalid TOML in "), log.output());
            assertTrue(log.output().contains("/mise.toml"), log.output());
            assertFalse(log.output().contains("/.mise.toml"), log.output());
        }
    }

    @Test
    void duplicateKeyInvalidatesFile() throws Exception {
        write("mise.toml", "[tools]\njava = \"17\"\njava = \"21\"\n");

        assertTrue(parser.parse(tempDir).isEmpty());
    }

    @Test
    void numbersAreVersions() throws Exception {
        write("mise.toml", "[tools]\nnode = 20\npython = 3.12\nruby = 3.0\n");

        Map<String, String> values = parser.parse(tempDir);

        assertEquals("20", values.get("mise.node"));
        assertEquals("3.12", values.get("mise.python"));
        assertEquals("3.0", values.get("mise.ruby"));
    }

    @Test
    void takesVersionFromToolTable() throws Exception {
        write("mise.toml", "[tools.node]\nversion = \"20\"\nos = [\"linux\"]\n");

        assertEquals("20", parser.parse(tempDir).get("mise.node"));
    }

    @Test
    void keysWithDotsAndColonsAreKeptWhole() throws Exception {
        write("mise.toml", "[tools]\n\"ubi:owner/repo.name\" = \"1.2\"\n");

        assertEquals("1.2", parser.parse(tempDir).get("mise.ubi:owner/repo.name"));
    }

    @Test
    void toolsWithoutUsableVersionWarnPerTool() throws Exception {
        write(
                "mise.toml",
                "[tools]\nempty = []\nflag = true\nblank = \"\"\n"
                        + "nover = { os = \"linux\" }\njava = \"21\"\n");

        try (LogCapture log = new LogCapture()) {
            Map<String, String> values = parser.parse(tempDir);

            assertEquals(1, values.size());
            assertEquals("21", values.get("mise.java"));
            String out = log.output();
            for (String tool : new String[] {"empty", "flag", "blank", "nover"}) {
                assertTrue(out.contains("Ignoring tool '" + tool + "'"), out);
            }
        }
    }

    @Test
    void invalidUtf8FileIsIgnoredWithError() throws Exception {
        Files.write(tempDir.resolve("mise.toml"), new byte[] {'[', 't', (byte) 0xC3, (byte) 0x28});
        write(".mise.toml", "[tools]\njava = \"17\"\n");

        try (LogCapture log = new LogCapture()) {
            assertEquals("17", parser.parse(tempDir).get("mise.java"));
            // "/mise.toml" with the slash: ".mise.toml" is the valid one and must not be named.
            assertTrue(log.output().contains("Invalid TOML in "), log.output());
            assertTrue(log.output().contains("/mise.toml"), log.output());
            assertFalse(log.output().contains("/.mise.toml"), log.output());
        }
    }

    @Test
    void readsDotMiseToml() throws Exception {
        write(".mise.toml", "[tools]\njava = \"21\"\n");

        assertEquals("21", parser.parse(tempDir).get("mise.java"));
    }

    @Test
    void miseTomlWinsOverDotMiseTomlOnSameKey() throws Exception {
        write(".mise.toml", "[tools]\njava = \"17\"\nnode = \"18\"\n");
        write("mise.toml", "[tools]\njava = \"21\"\n");

        Map<String, String> values = parser.parse(tempDir);

        assertEquals("21", values.get("mise.java"));
        assertEquals("18", values.get("mise.node"));
        assertEquals(2, values.size());
    }

    @Test
    void takesFirstElementOfMultilineArray() throws Exception {
        write("mise.toml", "[tools]\nnode = [\n  \"20\",\n  \"18\",\n]\njava = \"21\"\n");

        try (LogCapture log = new LogCapture()) {
            Map<String, String> values = parser.parse(tempDir);

            assertEquals("20", values.get("mise.node"));
            assertEquals("21", values.get("mise.java"));
            assertEquals(2, values.size());
            assertFalse(log.output().contains("WARN"), log.output());
        }
    }

    @Test
    void multilineArrayWithCommentsAndBlankLines() throws Exception {
        write(
                "mise.toml",
                "[tools]\nnode = [ # first\n\n  # pinned\n  \"20\", # lts\n  '18'\n  ]\n"
                        + "java = \"21\"\n");

        try (LogCapture log = new LogCapture()) {
            Map<String, String> values = parser.parse(tempDir);

            assertEquals("20", values.get("mise.node"));
            assertEquals("21", values.get("mise.java"));
            assertFalse(log.output().contains("WARN"), log.output());
        }
    }

    @Test
    void multilineArrayWithoutTrailingComma() throws Exception {
        write("mise.toml", "[tools]\nnode = [\n  \"20\"\n]\n");

        try (LogCapture log = new LogCapture()) {
            assertEquals("20", parser.parse(tempDir).get("mise.node"));
            assertFalse(log.output().contains("WARN"), log.output());
        }
    }

    @Test
    void unterminatedArrayIgnoresFileWithOneError() throws Exception {
        write("mise.toml", "[tools]\njava = \"21\"\nnode = [\n  \"20\",\n  \"18\"\n");

        try (LogCapture log = new LogCapture()) {
            assertTrue(parser.parse(tempDir).isEmpty());

            String out = log.output();
            assertTrue(out.contains("Invalid TOML"), out);
            assertEquals(1, out.split("ERROR", -1).length - 1, out);
        }
    }

    @Test
    void multilineArrayInOtherSectionIsIgnoredSilently() throws Exception {
        write("mise.toml", "[env]\nPATHS = [\n  \"a\",\n  \"b\",\n]\n[tools]\njava = \"21\"\n");

        try (LogCapture log = new LogCapture()) {
            Map<String, String> values = parser.parse(tempDir);

            assertEquals(1, values.size());
            assertEquals("21", values.get("mise.java"));
            assertFalse(log.output().contains("WARN"), log.output());
        }
    }

    @Test
    void syntaxErrorAfterMultilineArrayReportsRealLineNumber() throws Exception {
        write("mise.toml", "[tools]\nnode = [\n  \"20\",\n]\nbroken\n");

        try (LogCapture log = new LogCapture()) {
            parser.parse(tempDir);

            // The library reports line:column, so the position is the real line of the file.
            assertTrue(log.output().contains("5:"), log.output());
        }
    }
}

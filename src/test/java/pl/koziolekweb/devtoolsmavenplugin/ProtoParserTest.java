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

class ProtoParserTest {

    @TempDir Path tempDir;

    private final ProtoParser parser = new ProtoParser();

    private Map<String, String> parse(String content) throws Exception {
        Files.write(tempDir.resolve(".prototools"), content.getBytes(StandardCharsets.UTF_8));
        return parser.parse(tempDir);
    }

    @Test
    void parsesTopLevelEntries() throws Exception {
        Map<String, String> values = parse("node = \"20.0.0\"\nnpm = \"10\"\n");

        assertEquals("20.0.0", values.get("proto.node"));
        assertEquals("10", values.get("proto.npm"));
        assertEquals(2, values.size());
    }

    @Test
    void returnsEmptyMapWhenFileMissing() {
        assertTrue(parser.parse(tempDir).isEmpty());
    }

    @Test
    void supportsQuotingVariants() throws Exception {
        Map<String, String> values =
                parse("a = \"1.0\"\nb = '2.0'\nc = 3.0\nd=\"4.0\"\n\"e\" = \"5.0\"\n");

        assertEquals("1.0", values.get("proto.a"));
        assertEquals("2.0", values.get("proto.b"));
        assertEquals("3.0", values.get("proto.c"));
        assertEquals("4.0", values.get("proto.d"));
        assertEquals("5.0", values.get("proto.e"));
    }

    @Test
    void ignoresEverythingAfterFirstSection() throws Exception {
        Map<String, String> values =
                parse(
                        "node = \"20\"\n"
                                + "[plugins]\n"
                                + "foo = \"https://example.com/foo.toml\"\n"
                                + "[settings]\n"
                                + "bar = \"1\"\n"
                                + "[tools.node]\n"
                                + "baz = \"2\"\n");

        assertEquals("20", values.get("proto.node"));
        assertEquals(1, values.size());
    }

    @Test
    void ignoresKeysWhenFileStartsWithSection() throws Exception {
        assertTrue(parse("[plugins]\nfoo = \"1\"\n").isEmpty());
    }

    @Test
    void stripsCommentsAndSkipsCommentLines() throws Exception {
        Map<String, String> values =
                parse(
                        "# header\n"
                                + "\n"
                                + "node = \"20\" # quoted\n"
                                + "npm = 10 # bare\n"
                                + "pnpm = \"a#b\"\n");

        assertEquals("20", values.get("proto.node"));
        assertEquals("10", values.get("proto.npm"));
        assertEquals("a#b", values.get("proto.pnpm"));
        assertEquals(3, values.size());
    }

    @Test
    void numbersAreVersions() throws Exception {
        Map<String, String> values = parse("node = 20\npython = 3.12\n");

        assertEquals("20", values.get("proto.node"));
        assertEquals("3.12", values.get("proto.python"));
    }

    @Test
    void skipsTablesSilentlyAndOtherNonStringValuesWithWarn() throws Exception {
        try (LogCapture log = new LogCapture()) {
            Map<String, String> values =
                    parse(
                            "node = \"20\"\nlist = [\"a\", \"b\"]\nflag = true\n"
                                    + "tbl = { x = 1 }\nnpm = \"10\"\n");

            assertEquals("20", values.get("proto.node"));
            assertEquals("10", values.get("proto.npm"));
            assertEquals(2, values.size());
            String out = log.output();
            assertTrue(out.contains("'list'") && out.contains("'flag'"), out);
            assertFalse(out.contains("'tbl'"), out);
        }
    }

    @Test
    void ignoresWholeFileWithOneErrorOnSyntaxError() throws Exception {
        try (LogCapture log = new LogCapture()) {
            Map<String, String> values =
                    parse("node = \"20\"\nno-equals\nunterminated = \"1\nnpm = \"10\"\n");

            assertTrue(values.isEmpty());
            String out = log.output();
            assertTrue(out.contains("Invalid TOML in "), out);
            assertTrue(out.contains(".prototools"), out);
            assertEquals(1, out.split("ERROR", -1).length - 1, out);
        }
    }

    @Test
    void duplicateKeyInvalidatesFile() throws Exception {
        try (LogCapture log = new LogCapture()) {
            assertTrue(parse("node = \"18\"\nnode = \"20\"\n").isEmpty());
            assertTrue(log.output().contains("Invalid TOML"), log.output());
        }
    }

    @Test
    void invalidUtf8FileIsIgnoredWithError() throws Exception {
        Files.write(
                tempDir.resolve(".prototools"),
                new byte[] {'a', '=', '"', (byte) 0xC3, (byte) 0x28});

        try (LogCapture log = new LogCapture()) {
            assertTrue(parser.parse(tempDir).isEmpty());
            assertTrue(log.output().contains("ERROR"), log.output());
        }
    }
}

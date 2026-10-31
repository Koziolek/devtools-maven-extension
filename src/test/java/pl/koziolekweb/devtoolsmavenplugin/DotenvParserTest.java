package pl.koziolekweb.devtoolsmavenplugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DotenvParserTest {

    @TempDir Path tempDir;

    private final DotenvParser parser = new DotenvParser();

    private void write(String name, String content) throws Exception {
        Path path = tempDir.resolve(name);
        Files.createDirectories(path.getParent());
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
    }

    /** Most tests are about parsing, so by default every key is allowed. */
    @BeforeEach
    void allowEveryKey() throws Exception {
        allow("*");
    }

    private void allow(String keys) throws Exception {
        write(".mvn/devtools-extension.properties", "dotenv.allow=" + keys + "\n");
    }

    @Test
    void parsesEnvFile() throws Exception {
        write(".env", "FOO=bar\nDB_URL=jdbc:h2:mem\n");

        Map<String, String> values = parser.parse(tempDir);

        assertEquals("bar", values.get("dotenv.FOO"));
        assertEquals("jdbc:h2:mem", values.get("dotenv.DB_URL"));
        assertEquals(2, values.size());
    }

    @Test
    void returnsEmptyMapWhenNoFiles() {
        assertTrue(parser.parse(tempDir).isEmpty());
    }

    @Test
    void parsesEnvrcAndStripsExport() throws Exception {
        write(".envrc", "export FOO=bar\nexport   BAZ=qux\n");

        Map<String, String> values = parser.parse(tempDir);

        assertEquals("bar", values.get("dotenv.FOO"));
        assertEquals("qux", values.get("dotenv.BAZ"));
    }

    @Test
    void envWinsOverEnvrc() throws Exception {
        write(".env", "FOO=fromenv\n");
        write(".envrc", "export FOO=fromrc\nexport ONLY_RC=1\n");

        Map<String, String> values = parser.parse(tempDir);

        assertEquals("fromenv", values.get("dotenv.FOO"));
        assertEquals("1", values.get("dotenv.ONLY_RC"));
    }

    @Test
    void skipsBlankLinesAndComments() throws Exception {
        write(".env", "# comment\n\n   \nFOO=bar\n  # indented comment\n");

        Map<String, String> values = parser.parse(tempDir);

        assertEquals(1, values.size());
        assertEquals("bar", values.get("dotenv.FOO"));
    }

    @Test
    void trimsKeyAndValue() throws Exception {
        write(".env", "  FOO  =  bar baz  \n");

        assertEquals("bar baz", parser.parse(tempDir).get("dotenv.FOO"));
    }

    @Test
    void stripsDoubleAndSingleQuotes() throws Exception {
        write(".env", "A=\"hello world\"\nB='lit $X'\nC=\"it's\"\n");

        Map<String, String> values = parser.parse(tempDir);

        assertEquals("hello world", values.get("dotenv.A"));
        assertEquals("lit $X", values.get("dotenv.B"));
        assertEquals("it's", values.get("dotenv.C"));
    }

    @Test
    void keepsQuotedContentVerbatim() throws Exception {
        write(".env", "A=\"a\\nb # not comment\"\nB='x\\ty'\n");

        Map<String, String> values = parser.parse(tempDir);

        assertEquals("a\\nb # not comment", values.get("dotenv.A"));
        assertEquals("x\\ty", values.get("dotenv.B"));
    }

    @Test
    void allowsTrailingCommentAfterQuotedValue() throws Exception {
        write(".env", "A=\"v\" # note\n");

        assertEquals("v", parser.parse(tempDir).get("dotenv.A"));
    }

    @Test
    void stripsTrailingCommentFromUnquotedValue() throws Exception {
        write(".env", "A=value # comment\nB=a#b\nC=x\t# tab comment\n");

        Map<String, String> values = parser.parse(tempDir);

        assertEquals("value", values.get("dotenv.A"));
        assertEquals("a#b", values.get("dotenv.B"));
        assertEquals("x", values.get("dotenv.C"));
    }

    @Test
    void doesNotInterpolateVariables() throws Exception {
        write(".env", "A=$HOME/bin\nB=${FOO}-x\nFOO=1\n");

        Map<String, String> values = parser.parse(tempDir);

        assertEquals("$HOME/bin", values.get("dotenv.A"));
        assertEquals("${FOO}-x", values.get("dotenv.B"));
    }

    @Test
    void valueMayContainEquals() throws Exception {
        write(".env", "URL=http://x/?a=b\n");

        assertEquals("http://x/?a=b", parser.parse(tempDir).get("dotenv.URL"));
    }

    @Test
    void skipsDirenvCommandsInEnvrc() throws Exception {
        write(".envrc", "use nix\ndotenv\nPATH_add bin\nexport FOO=bar\nlayout python\n");

        Map<String, String> values = parser.parse(tempDir);

        assertEquals(1, values.size());
        assertEquals("bar", values.get("dotenv.FOO"));
    }

    @Test
    void skipsMalformedEnvLinesInsteadOfFailing() throws Exception {
        write(".env", "FOO=bar\nnot-a-pair\n=novalue\nBAZ=qux\n");

        Map<String, String> values = parser.parse(tempDir);

        assertEquals(2, values.size());
        assertEquals("bar", values.get("dotenv.FOO"));
        assertEquals("qux", values.get("dotenv.BAZ"));
    }

    @Test
    void skipsEmptyValues() throws Exception {
        write(".env", "A=\nB=\"\"\nC=''\nD=   # only comment\nE=ok\n");

        Map<String, String> values = parser.parse(tempDir);

        assertEquals(1, values.size());
        assertEquals("ok", values.get("dotenv.E"));
    }

    @Test
    void skipsInvalidKeys() throws Exception {
        write(".env", "1BAD=x\nbad-key=y\nbad key=z\nGOOD_1.x=ok\n_u=ok2\n");

        Map<String, String> values = parser.parse(tempDir);

        assertEquals(2, values.size());
        assertEquals("ok", values.get("dotenv.GOOD_1.x"));
        assertEquals("ok2", values.get("dotenv._u"));
        assertFalse(values.containsKey("dotenv.1BAD"));
    }

    @Test
    void usesDotenvPrefixNotEnv() throws Exception {
        write(".env", "PATH=/x\n");

        Map<String, String> values = parser.parse(tempDir);

        assertTrue(values.containsKey("dotenv.PATH"));
        assertFalse(values.containsKey("env.PATH"));
    }

    @Test
    void exportsNothingWithoutAllowListAndLogsInfo() throws Exception {
        Files.delete(tempDir.resolve(".mvn/devtools-extension.properties"));
        write(".env", "DB_PASSWORD=secret\nAPI_TOKEN=t0ken\n");

        try (LogCapture log = new LogCapture()) {
            Map<String, String> values = parser.parse(tempDir);

            assertTrue(values.isEmpty());
            assertTrue(log.output().contains("INFO"), log.output());
            assertTrue(log.output().contains(".env"), log.output());
            assertTrue(log.output().contains("API_TOKEN"), log.output());
            assertTrue(log.output().contains("DB_PASSWORD"), log.output());
            assertTrue(log.output().contains("dotenv.allow"), log.output());
            assertFalse(log.output().contains("secret"), log.output());
            assertFalse(log.output().contains("t0ken"), log.output());
        }
    }

    @Test
    void configWithoutDotenvAllowExportsNothing() throws Exception {
        write(
                ".mvn/devtools-extension.properties",
                "transform.a.source=java\ntransform.a.pattern=.*\n");
        write(".env", "FOO=bar\n");

        assertTrue(parser.parse(tempDir).isEmpty());
    }

    @Test
    void exportsOnlyAllowedKeys() throws Exception {
        allow("FOO, BAZ");
        write(".env", "FOO=bar\nDB_PASSWORD=secret\nBAZ=qux\n");

        try (LogCapture log = new LogCapture()) {
            Map<String, String> values = parser.parse(tempDir);

            assertEquals(2, values.size());
            assertEquals("bar", values.get("dotenv.FOO"));
            assertEquals("qux", values.get("dotenv.BAZ"));
            assertFalse(values.containsKey("dotenv.DB_PASSWORD"));
            assertEquals("", log.output());
        }
    }

    @Test
    void allowListAcceptsCommasAndWhitespaceAndIsCaseSensitive() throws Exception {
        allow("FOO,BAR   BAZ");
        write(".env", "FOO=1\nBAR=2\nBAZ=3\nfoo=4\n");

        Map<String, String> values = parser.parse(tempDir);

        assertEquals(3, values.size());
        assertFalse(values.containsKey("dotenv.foo"));
    }

    @Test
    void allowListAppliesToEnvrcToo() throws Exception {
        allow("FOO");
        write(".envrc", "export FOO=bar\nexport SECRET=x\n");

        Map<String, String> values = parser.parse(tempDir);

        assertEquals(1, values.size());
        assertEquals("bar", values.get("dotenv.FOO"));
    }

    @Test
    void logsInfoWhenNoKeyOfTheFileIsAllowed() throws Exception {
        allow("OTHER");
        write(".env", "FOO=bar\n");

        try (LogCapture log = new LogCapture()) {
            assertTrue(parser.parse(tempDir).isEmpty());

            assertTrue(log.output().contains("INFO"), log.output());
            assertTrue(log.output().contains("FOO"), log.output());
        }
    }

    @Test
    void doesNotLogWhenFileHasNoAssignments() throws Exception {
        Files.delete(tempDir.resolve(".mvn/devtools-extension.properties"));
        write(".env", "# only a comment\n\n");

        try (LogCapture log = new LogCapture()) {
            assertTrue(parser.parse(tempDir).isEmpty());

            assertEquals("", log.output());
        }
    }

    @Test
    void doesNotLogWhenThereAreNoFiles() throws Exception {
        Files.delete(tempDir.resolve(".mvn/devtools-extension.properties"));

        try (LogCapture log = new LogCapture()) {
            assertTrue(parser.parse(tempDir).isEmpty());

            assertEquals("", log.output());
        }
    }

    @Test
    void malformedAllowConfigurationIsLoggedAtErrorAndNothingIsExported() throws Exception {
        write(".env", "FOO=bar\n");
        write(".mvn/devtools-extension.properties", "dotenv.allow=\\u12G4\n");
        try (LogCapture log = new LogCapture()) {
            assertTrue(parser.parse(tempDir).isEmpty());

            String out = log.output();
            assertTrue(out.contains("ERROR"), out);
            assertTrue(
                    out.contains(
                            "Error reading file: "
                                    + tempDir.resolve(".mvn/devtools-extension.properties")),
                    out);
        }
    }

    @Test
    void exportWithoutWhitespaceAfterItIsPlainKey() throws Exception {
        write(".env", "exportFOO=1\nexport=2\nexport\t TAB=3\nexport\n");

        Map<String, String> values = parser.parse(tempDir);

        assertEquals("1", values.get("dotenv.exportFOO"));
        assertEquals("2", values.get("dotenv.export"));
        assertEquals("3", values.get("dotenv.TAB"));
        assertEquals(3, values.size());
    }

    @Test
    void quoteWithoutClosingQuoteOrWithTrailingTextIsTakenLiterally() throws Exception {
        write(".env", "A=\"abc\nB=\"abc\" def\nC='x' # comment\nD=\"y\"z\n");

        Map<String, String> values = parser.parse(tempDir);

        assertEquals("\"abc", values.get("dotenv.A"));
        assertEquals("\"abc\" def", values.get("dotenv.B"));
        assertEquals("x", values.get("dotenv.C"));
        assertEquals("\"y\"z", values.get("dotenv.D"));
    }
}

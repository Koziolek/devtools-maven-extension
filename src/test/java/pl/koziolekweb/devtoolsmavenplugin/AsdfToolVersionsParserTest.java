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

class AsdfToolVersionsParserTest {

    private final AsdfToolVersionsParser parser = new AsdfToolVersionsParser();
    public static final String TOOL_VERSIONS_FILE_NAME = AsdfToolVersionsParser.FILE_NAME;
    @TempDir Path tempDir;

    @Test
    void parsesToolVersions() throws Exception {
        Files.write(
                tempDir.resolve(TOOL_VERSIONS_FILE_NAME),
                ("nodejs 22.15.0\npython 3.7.2 2.7.15 system\n#comment\n\n")
                        .getBytes(StandardCharsets.UTF_8));

        Map<String, String> values = parser.parse(tempDir);

        assertEquals("22.15.0", values.get("asdf.nodejs"));
        assertEquals("3.7.2 2.7.15 system", values.get("asdf.python"));
    }

    @Test
    void parsesEmptyToolVersions() throws Exception {
        Files.write(
                tempDir.resolve(TOOL_VERSIONS_FILE_NAME), ("  ").getBytes(StandardCharsets.UTF_8));

        assertTrue(parser.parse(tempDir).isEmpty());
    }

    @Test
    void returnsEmptyMapWhenFileMissing() {
        assertTrue(parser.parse(tempDir).isEmpty());
    }

    @Test
    void skipsMalformedLineInsteadOfFailing() throws Exception {
        Files.write(
                tempDir.resolve(TOOL_VERSIONS_FILE_NAME),
                ("nodejs 22.15.0\ninvalid-line-no-version\npython 3.12.3\n")
                        .getBytes(StandardCharsets.UTF_8));

        Map<String, String> values = parser.parse(tempDir);

        assertEquals("22.15.0", values.get("asdf.nodejs"));
        assertEquals("3.12.3", values.get("asdf.python"));
        assertEquals(2, values.size());
    }

    @ParameterizedTest
    @MethodSource("pl.koziolekweb.devtoolsmavenplugin.ParserFixtures#asdfSingleValueExpected")
    void rejectsToolNameWithNoVersion(String fileContent, String toolName, String version)
            throws Exception {
        Files.write(
                tempDir.resolve(TOOL_VERSIONS_FILE_NAME),
                (fileContent).getBytes(StandardCharsets.UTF_8));

        Map<String, String> values = parser.parse(tempDir);

        assertEquals(version, values.get(toolName));
        assertEquals(1, values.size());
    }

    @Test
    void stripsTrailingComment() throws Exception {
        Files.write(
                tempDir.resolve(TOOL_VERSIONS_FILE_NAME),
                ("nodejs 20.1.0 # LTS\npython 3.12.3 #x\nruby 3.3.0#c\n")
                        .getBytes(StandardCharsets.UTF_8));

        Map<String, String> values = parser.parse(tempDir);

        assertEquals("20.1.0", values.get("asdf.nodejs"));
        assertEquals("3.12.3", values.get("asdf.python"));
        assertEquals("3.3.0", values.get("asdf.ruby"));
    }

    @Test
    void acceptsTabAndMultipleSpacesAsSeparator() throws Exception {
        Files.write(
                tempDir.resolve(TOOL_VERSIONS_FILE_NAME),
                ("nodejs\t20.1.0\npython   3.7.2 \t 2.7.15\t system\n")
                        .getBytes(StandardCharsets.UTF_8));

        Map<String, String> values = parser.parse(tempDir);

        assertEquals("20.1.0", values.get("asdf.nodejs"));
        assertEquals("3.7.2 \t 2.7.15\t system", values.get("asdf.python"));
    }

    @Test
    void keepsSpecialValuesVerbatim() throws Exception {
        Files.write(
                tempDir.resolve(TOOL_VERSIONS_FILE_NAME),
                ("java system\nruby ref:master\nlua path:/opt/lua\n")
                        .getBytes(StandardCharsets.UTF_8));

        Map<String, String> values = parser.parse(tempDir);

        assertEquals("system", values.get("asdf.java"));
        assertEquals("ref:master", values.get("asdf.ruby"));
        assertEquals("path:/opt/lua", values.get("asdf.lua"));
    }
}

package pl.koziolekweb.devtoolsmavenplugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JabbaParserTest {

    @TempDir Path tempDir;

    private final JabbaParser parser = new JabbaParser();

    private void write(String content) throws Exception {
        Files.write(tempDir.resolve(".jabbrc"), content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void parsesJdk() throws Exception {
        write("jdk: openjdk@1.11.0\n");

        Map<String, String> values = parser.parse(tempDir);

        assertEquals("openjdk@1.11.0", values.get("jabba.jdk"));
        assertEquals(1, values.size());
    }

    @Test
    void returnsEmptyMapWhenFileMissing() {
        assertTrue(parser.parse(tempDir).isEmpty());
    }

    @Test
    void ignoresCommentsAndBlankLines() throws Exception {
        write("# comment\n\njdk: openjdk@1.11.0\n   \n# another\n");

        Map<String, String> values = parser.parse(tempDir);

        assertEquals("openjdk@1.11.0", values.get("jabba.jdk"));
        assertEquals(1, values.size());
    }

    @Test
    void skipsMalformedLineInsteadOfFailing() throws Exception {
        write("jdk: openjdk@1.11.0\nnot-a-valid-line\n: empty-key\nempty:\nother: 1.0\n");

        Map<String, String> values = parser.parse(tempDir);

        assertEquals("openjdk@1.11.0", values.get("jabba.jdk"));
        assertEquals("1.0", values.get("jabba.other"));
        assertEquals(2, values.size());
    }

    @Test
    void keepsFurtherColonsInValueVerbatim() throws Exception {
        write("jdk: openjdk@1.11.0:extra:bits\n");

        assertEquals("openjdk@1.11.0:extra:bits", parser.parse(tempDir).get("jabba.jdk"));
    }
}

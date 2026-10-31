package pl.koziolekweb.devtoolsmavenplugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PyenvParserTest {

    @TempDir Path tempDir;

    private final PyenvParser parser = new PyenvParser();

    @Test
    void parsesVersionFile() throws Exception {
        Files.write(
                tempDir.resolve(".python-version"),
                "# pin\n\n1.2.3\n".getBytes(StandardCharsets.UTF_8));

        Map<String, String> values = parser.parse(tempDir);

        assertEquals("1.2.3", values.get("pyenv.python"));
        assertEquals(1, values.size());
    }

    @Test
    void returnsEmptyMapWhenFileMissing() {
        assertTrue(parser.parse(tempDir).isEmpty());
    }
}

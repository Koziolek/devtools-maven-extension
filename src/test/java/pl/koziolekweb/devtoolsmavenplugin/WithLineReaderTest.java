package pl.koziolekweb.devtoolsmavenplugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WithLineReaderTest {

    private static final class Reader implements WithLineReader {}

    @TempDir Path tempDir;

    private final WithLineReader reader = new Reader();

    private void write(String name, String content) throws Exception {
        Path path = tempDir.resolve(name);
        Files.createDirectories(path.getParent());
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void returnsLinesOfExistingFile() throws Exception {
        write(".sdkmanrc", "java=25-open\nquarkus=3.39.4\n");

        Optional<List<String>> lines = reader.readLines(tempDir, ".sdkmanrc");

        assertEquals(Optional.of(Arrays.asList("java=25-open", "quarkus=3.39.4")), lines);
    }

    @Test
    void returnsEmptyOptionalWhenFileMissing() {
        assertFalse(reader.readLines(tempDir, ".sdkmanrc").isPresent());
    }

    @Test
    void returnsEmptyOptionalWhenPathIsDirectory() throws Exception {
        Files.createDirectory(tempDir.resolve(".sdkmanrc"));

        assertFalse(reader.readLines(tempDir, ".sdkmanrc").isPresent());
    }

    @Test
    void returnsPresentEmptyListForEmptyFile() throws Exception {
        write(".sdkmanrc", "");

        Optional<List<String>> lines = reader.readLines(tempDir, ".sdkmanrc");

        assertTrue(lines.isPresent());
        assertEquals(Collections.emptyList(), lines.get());
    }

    @Test
    void keepsBlankLinesCommentsAndWhitespaceUntouched() throws Exception {
        write(".tool-versions", "# pinned\n\n  java 21 \nnodejs 22\n");

        List<String> lines = reader.readLines(tempDir, ".tool-versions").get();

        assertEquals(Arrays.asList("# pinned", "", "  java 21 ", "nodejs 22"), lines);
    }

    @Test
    void lineIndexMapsToLineNumber() throws Exception {
        write(".sdkmanrc", "# comment\n\njava=25-open\n");

        List<String> lines = reader.readLines(tempDir, ".sdkmanrc").get();

        assertEquals("java=25-open", lines.get(3 - 1));
    }

    @Test
    void stripsLineTerminators() throws Exception {
        write(".sdkmanrc", "a=1\r\nb=2\rc=3\n");

        List<String> lines = reader.readLines(tempDir, ".sdkmanrc").get();

        assertEquals(Arrays.asList("a=1", "b=2", "c=3"), lines);
    }

    @Test
    void lastLineWithoutTrailingNewlineIsKept() throws Exception {
        write(".sdkmanrc", "a=1\nb=2");

        List<String> lines = reader.readLines(tempDir, ".sdkmanrc").get();

        assertEquals(Arrays.asList("a=1", "b=2"), lines);
    }

    @Test
    void decodesUtf8() throws Exception {
        write(".sdkmanrc", "opis=zażółć gęślą jaźń\n");

        List<String> lines = reader.readLines(tempDir, ".sdkmanrc").get();

        assertEquals(Collections.singletonList("opis=zażółć gęślą jaźń"), lines);
    }

    @Test
    void resolvesNestedRelativePath() throws Exception {
        write(".mvn/wrapper/maven-wrapper.properties", "wrapperVersion=3.3.2\n");

        Optional<List<String>> lines =
                reader.readLines(tempDir, ".mvn/wrapper/maven-wrapper.properties");

        assertEquals(Optional.of(Collections.singletonList("wrapperVersion=3.3.2")), lines);
    }

    @Test
    void logsErrorAndReturnsEmptyForInvalidUtf8() throws Exception {
        Path file = tempDir.resolve(".sdkmanrc");
        Files.write(file, new byte[] {'a', '=', (byte) 0xC3, (byte) 0x28});

        try (LogCapture log = new LogCapture()) {
            Optional<List<String>> lines = reader.readLines(tempDir, ".sdkmanrc");

            assertEquals(Optional.empty(), lines);
            assertTrue(log.output().contains("ERROR"), log.output());
            assertTrue(log.output().contains("Error reading file"), log.output());
            assertTrue(log.output().contains(file.toString()), log.output());
            assertTrue(log.output().contains("MalformedInputException"), log.output());
        }
    }

    @Test
    void logsNothingForMissingFileAndDirectory() throws Exception {
        Files.createDirectories(tempDir.resolve(".sdkmanrc"));

        try (LogCapture log = new LogCapture()) {
            assertEquals(Optional.empty(), reader.readLines(tempDir, ".sdkmanrc"));
            assertEquals(Optional.empty(), reader.readLines(tempDir, "absent"));

            assertEquals("", log.output());
        }
    }

    private static Map<String, String> firstLineAsJava(List<String> lines) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("test.java", lines.get(0));
        return values;
    }

    @Test
    void processorResultIsReturnedAsIs() throws Exception {
        write(".sdkmanrc", "25-open\n");
        Map<String, String> expected = Collections.singletonMap("test.java", "25-open");

        Map<String, String> values =
                reader.readAndProcessLines(tempDir, ".sdkmanrc", lines -> expected);

        assertSame(expected, values);
    }

    @Test
    void processorReceivesRawLines() throws Exception {
        write(".sdkmanrc", "25-open\nlast\n");

        Map<String, String> values =
                reader.readAndProcessLines(
                        tempDir, ".sdkmanrc", WithLineReaderTest::firstLineAsJava);

        assertEquals(Collections.singletonMap("test.java", "25-open"), values);
    }

    @Test
    void processorIsNotCalledWhenFileMissing() {
        AtomicInteger calls = new AtomicInteger();

        Map<String, String> values =
                reader.readAndProcessLines(
                        tempDir,
                        ".sdkmanrc",
                        lines -> {
                            calls.incrementAndGet();
                            return Collections.singletonMap("test.java", "x");
                        });

        assertTrue(values.isEmpty());
        assertEquals(0, calls.get());
    }

    @Test
    void processorIsNotCalledWhenPathIsDirectory() throws Exception {
        Files.createDirectory(tempDir.resolve(".sdkmanrc"));
        AtomicInteger calls = new AtomicInteger();

        Map<String, String> values =
                reader.readAndProcessLines(
                        tempDir,
                        ".sdkmanrc",
                        lines -> {
                            calls.incrementAndGet();
                            return Collections.singletonMap("test.java", "x");
                        });

        assertTrue(values.isEmpty());
        assertEquals(0, calls.get());
    }

    @Test
    void processorIsCalledWithEmptyListForEmptyFile() throws Exception {
        write(".sdkmanrc", "");
        AtomicInteger calls = new AtomicInteger();

        Map<String, String> values =
                reader.readAndProcessLines(
                        tempDir,
                        ".sdkmanrc",
                        lines -> {
                            calls.incrementAndGet();
                            assertTrue(lines.isEmpty());
                            return Collections.singletonMap("test.java", "from-empty");
                        });

        assertEquals(1, calls.get());
        assertEquals(Collections.singletonMap("test.java", "from-empty"), values);
    }

    @Test
    void nullFromProcessorBecomesEmptyMap() throws Exception {
        write(".sdkmanrc", "25-open\n");

        Map<String, String> values =
                reader.readAndProcessLines(tempDir, ".sdkmanrc", lines -> null);

        assertTrue(values.isEmpty());
    }

    @Test
    void processorExceptionPropagates() throws Exception {
        write(".sdkmanrc", "25-open\n");

        assertThrows(
                IllegalStateException.class,
                () ->
                        reader.readAndProcessLines(
                                tempDir,
                                ".sdkmanrc",
                                lines -> {
                                    throw new IllegalStateException("boom");
                                }));
    }

    @Test
    void invalidUtf8LogsErrorAndReturnsEmptyMapWithoutCallingProcessor() throws Exception {
        Path file = tempDir.resolve(".sdkmanrc");
        Files.write(file, new byte[] {'a', '=', (byte) 0xC3, (byte) 0x28});
        AtomicInteger calls = new AtomicInteger();

        try (LogCapture log = new LogCapture()) {
            Map<String, String> result =
                    reader.readAndProcessLines(
                            tempDir,
                            ".sdkmanrc",
                            lines -> {
                                calls.incrementAndGet();
                                return Collections.singletonMap("k", "v");
                            });

            assertTrue(result.isEmpty());
            assertEquals(0, calls.get());
            assertTrue(log.output().contains("Error reading file"), log.output());
            assertTrue(log.output().contains(file.toString()), log.output());
        }
    }
}

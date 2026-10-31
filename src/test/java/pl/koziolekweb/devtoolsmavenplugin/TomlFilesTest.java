package pl.koziolekweb.devtoolsmavenplugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;

class TomlFilesTest {

    @TempDir Path tempDir;

    private Path write(String content) throws Exception {
        Path file = tempDir.resolve("test.toml");
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    // --- readToml: result tree ---

    @Test
    void readsScalarsArraysAndTables() throws Exception {
        Map<String, Object> root =
                TomlFiles.readToml(
                        write(
                                "s = \"text\"\nn = 20\nf = 3.5\nb = true\nlist = [\"a\", \"b\"]\n"
                                        + "[tools]\nnode = \"20\"\n"));

        assertEquals("text", root.get("s"));
        assertEquals(20L, root.get("n"));
        assertEquals(3.5d, root.get("f"));
        assertEquals(Boolean.TRUE, root.get("b"));
        assertEquals(Arrays.asList("a", "b"), root.get("list"));
        assertEquals("20", ((Map<?, ?>) root.get("tools")).get("node"));
    }

    @Test
    void readsMultiByteUtf8() throws Exception {
        Map<String, Object> root = TomlFiles.readToml(write("a = \"é€😀\"\n"));

        assertEquals("é€😀", root.get("a"));
    }

    // --- readToml: silent empty results ---

    @Test
    void missingFileGivesEmptyMapWithoutLogging() {
        try (LogCapture log = new LogCapture()) {
            assertTrue(TomlFiles.readToml(tempDir.resolve("nope.toml")).isEmpty());
            assertEquals("", log.output());
        }
    }

    @Test
    void directoryGivesEmptyMapWithoutLogging() {
        try (LogCapture log = new LogCapture()) {
            assertTrue(TomlFiles.readToml(tempDir).isEmpty());
            assertEquals("", log.output());
        }
    }

    @Test
    void emptyFileGivesEmptyMapWithoutLogging() throws Exception {
        try (LogCapture log = new LogCapture()) {
            assertTrue(TomlFiles.readToml(write("")).isEmpty());
            assertEquals("", log.output());
        }
    }

    // --- readToml: I/O errors ---

    /**
     * toml-java catches the decoding error of the reader itself and reports it as a TOML error, so
     * invalid UTF-8 never reaches the {@code IOException} branch of {@link TomlFiles}.
     */
    @Test
    void invalidUtf8IsLoggedAtErrorAsInvalidToml() throws Exception {
        Path file = tempDir.resolve("bad.toml");
        Files.write(file, new byte[] {'a', ' ', '=', ' ', '"', (byte) 0xC3, 0x28, '"', '\n'});
        try (LogCapture log = new LogCapture()) {
            assertTrue(TomlFiles.readToml(file).isEmpty());

            String out = log.output();
            assertTrue(out.contains("ERROR"), out);
            assertTrue(out.contains("Invalid TOML in " + file), out);
            assertTrue(out.contains("Malformed UTF-8"), out);
        }
    }

    @Test
    void fileThatCannotBeOpenedIsLoggedAtErrorWithTheException() throws Exception {
        Path file = write("a = 1\n");
        Files.setPosixFilePermissions(file, Collections.<PosixFilePermission>emptySet());
        try (LogCapture log = new LogCapture()) {
            Assumptions.assumeFalse(Files.isReadable(file), "root or non-POSIX FS reads it anyway");

            assertTrue(TomlFiles.readToml(file).isEmpty());

            String out = log.output();
            assertTrue(out.contains("ERROR"), out);
            assertTrue(out.contains("Error reading file: " + file), out);
            assertTrue(out.contains("AccessDeniedException"), out);
        } finally {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        }
    }

    @Test
    void invalidUtf8LateInLargeFileIsStillReportedAsInvalidToml() throws Exception {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 2000; i++) {
            text.append("key").append(i).append(" = \"value\"\n");
        }
        byte[] valid = text.toString().getBytes(StandardCharsets.UTF_8);
        byte[] bytes = Arrays.copyOf(valid, valid.length + 4);
        bytes[valid.length] = '#';
        bytes[valid.length + 1] = ' ';
        bytes[valid.length + 2] = (byte) 0xC3;
        bytes[valid.length + 3] = 0x28;
        Path file = tempDir.resolve("big.toml");
        Files.write(file, bytes);
        try (LogCapture log = new LogCapture()) {
            assertTrue(TomlFiles.readToml(file).isEmpty());
            assertTrue(log.output().contains("Invalid TOML in " + file), log.output());
        }
    }

    // --- readToml: syntax errors ---

    @ParameterizedTest
    @CsvSource({
        "'a = '",
        "'a = \"unterminated'",
        "'[tools'",
        "'a = 1\\na = 2'",
        "'[t]\\n[t]'",
        "'a b = 1'"
    })
    void syntaxErrorIsLoggedAtErrorAndIgnoresTheWholeFile(String toml) throws Exception {
        Path file = write("ok = 1\n" + toml.replace("\\n", "\n") + "\n");
        try (LogCapture log = new LogCapture()) {
            assertTrue(TomlFiles.readToml(file).isEmpty());

            String out = log.output();
            assertTrue(out.contains("ERROR"), out);
            assertTrue(out.contains("Invalid TOML in " + file), out);
        }
    }

    @Test
    void syntaxErrorGivesEmptyMapAlsoWhenErrorLoggingIsDisabled() throws Exception {
        Path file = write("ok = 1\nbroken = \n");
        try (LogCapture log = new LogCapture();
                LoggerLevel off = LoggerLevel.disable(TomlFiles.class)) {
            assertTrue(TomlFiles.readToml(file).isEmpty());
            assertEquals("", log.output());
        }
    }

    /** Switches a slf4j-simple logger off for the duration of a test. */
    private static final class LoggerLevel implements AutoCloseable {
        private final Object logger;
        private final Field level;
        private final int original;

        private LoggerLevel(Object logger, Field level) throws ReflectiveOperationException {
            this.logger = logger;
            this.level = level;
            this.original = level.getInt(logger);
        }

        static LoggerLevel disable(Class<?> owner) throws ReflectiveOperationException {
            Object logger = LoggerFactory.getLogger(owner);
            Field level = logger.getClass().getDeclaredField("currentLogLevel");
            level.setAccessible(true);
            LoggerLevel result = new LoggerLevel(logger, level);
            level.setInt(logger, Integer.MAX_VALUE);
            return result;
        }

        @Override
        public void close() throws ReflectiveOperationException {
            level.setInt(logger, original);
        }
    }

    // --- scalarText ---

    @ParameterizedTest
    @CsvSource({"20, 20", "-3, -3", "0, 0"})
    void longIsRenderedAsIs(long value, String expected) {
        assertEquals(expected, TomlFiles.scalarText(value));
    }

    @ParameterizedTest
    @CsvSource({"3.0, 3.0", "3.1, 3.1", "0.0015, 0.0015", "1.0E10, 10000000000", "-2.5, -2.5"})
    void doubleIsRenderedWithoutExponent(double value, String expected) {
        assertEquals(expected, TomlFiles.scalarText(value));
    }

    @Test
    void stringIsReturnedUnlessEmpty() {
        assertEquals("21-tem", TomlFiles.scalarText("21-tem"));
        assertEquals(" ", TomlFiles.scalarText(" "));
        assertNull(TomlFiles.scalarText(""));
    }

    @Test
    void everythingElseIsNull() {
        assertNull(TomlFiles.scalarText(null));
        assertNull(TomlFiles.scalarText(Boolean.TRUE));
        assertNull(TomlFiles.scalarText(Double.NaN));
        assertNull(TomlFiles.scalarText(Double.POSITIVE_INFINITY));
        assertNull(TomlFiles.scalarText(Double.NEGATIVE_INFINITY));
        assertNull(TomlFiles.scalarText(Arrays.asList("a")));
        assertNull(TomlFiles.scalarText(new BigDecimal("1")));
    }
}

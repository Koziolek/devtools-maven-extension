package pl.koziolekweb.devtoolsmavenplugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MavenWrapperParserTest {

    @TempDir Path tempDir;

    private final MavenWrapperParser parser = new MavenWrapperParser();

    private void write(String content) throws Exception {
        Path file = tempDir.resolve(".mvn/wrapper/maven-wrapper.properties");
        Files.createDirectories(file.getParent());
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void unescapesUrlAndDerivesMavenVersion() throws Exception {
        write(
                "wrapperVersion=3.3.2\n"
                        + "distributionType=only-script\n"
                        + "distributionUrl=https\\://repo.maven.apache.org/maven2/org/apache/maven/"
                        + "apache-maven/3.9.9/apache-maven-3.9.9-bin.zip\n");

        Map<String, String> values = parser.parse(tempDir);

        assertEquals(
                "https://repo.maven.apache.org/maven2/org/apache/maven/"
                        + "apache-maven/3.9.9/apache-maven-3.9.9-bin.zip",
                values.get("mvnw.distributionUrl"));
        assertEquals("3.3.2", values.get("mvnw.wrapperVersion"));
        assertEquals("only-script", values.get("mvnw.distributionType"));
        assertEquals("3.9.9", values.get("mvnw.maven"));
        assertEquals(4, values.size());
    }

    @Test
    void derivesReleaseCandidateVersion() throws Exception {
        write(
                "distributionUrl=https\\://host/apache-maven/4.0.0-rc-3/"
                        + "apache-maven-4.0.0-rc-3-bin.zip\n");

        assertEquals("4.0.0-rc-3", parser.parse(tempDir).get("mvnw.maven"));
    }

    @Test
    void derivesAlphaAndBetaVersions() throws Exception {
        write("distributionUrl=https\\://host/apache-maven-4.0.0-alpha-13-bin.tar.gz\n");
        assertEquals("4.0.0-alpha-13", parser.parse(tempDir).get("mvnw.maven"));

        write("distributionUrl=https\\://host/apache-maven-4.0.0-beta-5-bin.zip\n");
        assertEquals("4.0.0-beta-5", parser.parse(tempDir).get("mvnw.maven"));
    }

    @Test
    void derivesVersionFromSrcVariant() throws Exception {
        write("distributionUrl=https\\://host/apache-maven-3.8.8-src.zip\n");

        assertEquals("3.8.8", parser.parse(tempDir).get("mvnw.maven"));
    }

    @Test
    void returnsEmptyMapWhenFileMissing() {
        assertTrue(parser.parse(tempDir).isEmpty());
    }

    @Test
    void omitsMavenWhenUrlDoesNotMatch() throws Exception {
        write("distributionUrl=https\\://internal.example.com/maven/custom-dist.zip\n");

        Map<String, String> values = parser.parse(tempDir);

        assertEquals(
                "https://internal.example.com/maven/custom-dist.zip",
                values.get("mvnw.distributionUrl"));
        assertFalse(values.containsKey("mvnw.maven"));
    }

    @Test
    void omitsMavenWhenDistributionUrlAbsent() throws Exception {
        write("wrapperVersion=3.3.2\n");

        Map<String, String> values = parser.parse(tempDir);

        assertEquals("3.3.2", values.get("mvnw.wrapperVersion"));
        assertFalse(values.containsKey("mvnw.maven"));
        assertEquals(1, values.size());
    }

    @Test
    void skipsBlankValuesAndComments() throws Exception {
        write("# comment\nwrapperVersion=\ndistributionType=only-script\n");

        Map<String, String> values = parser.parse(tempDir);

        assertEquals(1, values.size());
        assertEquals("only-script", values.get("mvnw.distributionType"));
    }

    @Test
    void skipsEntriesWithAnEmptyOrBlankName() throws Exception {
        write("=empty-name\n\\u0020=blank-name\n\\ \\ =escaped-spaces\nwrapperVersion=3.3.2\n");

        Map<String, String> values = parser.parse(tempDir);

        assertEquals(1, values.size());
        assertEquals("3.3.2", values.get("mvnw.wrapperVersion"));
    }

    @Test
    void trimsNameAndValue() throws Exception {
        write("\\u0020spaced\\u0020=  padded value  \n");

        assertEquals("padded value", parser.parse(tempDir).get("mvnw.spaced"));
    }

    @Test
    void malformedUnicodeEscapeIsLoggedAtErrorAndIgnoresTheWholeFile() throws Exception {
        write("wrapperVersion=3.3.2\nbroken=\\u12G4\n");
        Path file = tempDir.resolve(".mvn/wrapper/maven-wrapper.properties");
        try (LogCapture log = new LogCapture()) {
            assertTrue(parser.parse(tempDir).isEmpty());

            String out = log.output();
            assertTrue(out.contains("ERROR"), out);
            assertTrue(out.contains("Error reading file: " + file), out);
            assertTrue(out.contains("Malformed"), out);
        }
    }

    @Test
    void invalidUtf8IsLoggedAtErrorAndIgnoresTheWholeFile() throws Exception {
        Path file = tempDir.resolve(".mvn/wrapper/maven-wrapper.properties");
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[] {'a', '=', (byte) 0xC3, 0x28, '\n', 'b', '=', '1', '\n'});
        try (LogCapture log = new LogCapture()) {
            assertTrue(parser.parse(tempDir).isEmpty());

            String out = log.output();
            assertTrue(out.contains("Error reading file: " + file), out);
            assertTrue(
                    out.contains("MalformedInputException") || out.contains("Input length"), out);
        }
    }

    @Test
    void fileThatCannotBeOpenedIsLoggedAtError() throws Exception {
        write("wrapperVersion=3.3.2\n");
        Path file = tempDir.resolve(".mvn/wrapper/maven-wrapper.properties");
        Files.setPosixFilePermissions(file, Collections.<PosixFilePermission>emptySet());
        try (LogCapture log = new LogCapture()) {
            Assumptions.assumeFalse(Files.isReadable(file), "root or non-POSIX FS reads it anyway");

            assertTrue(parser.parse(tempDir).isEmpty());
            assertTrue(log.output().contains("Error reading file: " + file), log.output());
        } finally {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        }
    }

    @Test
    void directoryInPlaceOfTheFileGivesEmptyMapWithoutLogging() throws Exception {
        Files.createDirectories(tempDir.resolve(".mvn/wrapper/maven-wrapper.properties"));
        try (LogCapture log = new LogCapture()) {
            assertTrue(parser.parse(tempDir).isEmpty());
            assertEquals("", log.output());
        }
    }

    @Test
    void warnsWhenMavenVersionCannotBeDerived() throws Exception {
        write("distributionUrl=https\\://host/custom-dist.zip\n");
        try (LogCapture log = new LogCapture()) {
            parser.parse(tempDir);

            assertTrue(log.output().contains("Cannot derive Maven version"), log.output());
        }
    }
}

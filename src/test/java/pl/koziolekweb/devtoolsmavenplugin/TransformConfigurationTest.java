package pl.koziolekweb.devtoolsmavenplugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TransformConfigurationTest {

    /** Alphabetical, as {@link DevtoolsLifecycleParticipant#namespacesOf} produces them. */
    private static final List<String> NAMESPACES =
            Arrays.asList("asdf.", "foo.", "jenv.", "mise.", "nvm.", "sdkman.");

    @TempDir Path tempDir;

    @Test
    void extractsNumericJavaVersion() throws Exception {
        Path file = tempDir.resolve("sdkman-extension.properties");
        Files.write(
                file,
                ("transform.java.version.source=java\n"
                                + "transform.java.version.pattern=^([0-9]+).*$\n"
                                + "transform.java.version.replacement=$1\n")
                        .getBytes(StandardCharsets.ISO_8859_1));

        Properties properties = new Properties();
        properties.setProperty("sdkman.java", "25-open");

        TransformConfiguration.load(file, NAMESPACES).apply(properties);

        assertEquals("25", properties.getProperty("sdkman.java.version"));
    }

    @Test
    void extractsFromAsdfSourceWhenNotInSdkman() throws Exception {
        Path file = tempDir.resolve("devtools-extension.properties");
        Files.write(
                file,
                ("transform.node.major.source=nodejs\n"
                                + "transform.node.major.pattern=^([0-9]+).*$\n"
                                + "transform.node.major.replacement=$1\n")
                        .getBytes(StandardCharsets.ISO_8859_1));

        Properties properties = new Properties();
        properties.setProperty("asdf.nodejs", "22.15.0");

        TransformConfiguration.load(file, NAMESPACES).apply(properties);

        assertEquals("22", properties.getProperty("asdf.node.major"));
    }

    @Test
    void extractsVersionAndVendorFromAsdfFallbackList() throws Exception {
        Path file = tempDir.resolve("devtools-extension.properties");
        Files.write(
                file,
                ("transform.python.version.source=asdf.python\n"
                                + "transform.python.version.pattern=^(\\\\S+).*$\n"
                                + "transform.python.version.replacement=$1\n"
                                + "transform.python.vendor.source=asdf.python\n"
                                + "transform.python.vendor.pattern=.*\\\\s(\\\\S+)$\n"
                                + "transform.python.vendor.replacement=$1\n")
                        .getBytes(StandardCharsets.ISO_8859_1));

        Properties properties = new Properties();
        properties.setProperty("asdf.python", "3.7.2 2.7.15 system");

        TransformConfiguration.load(file, NAMESPACES).apply(properties);

        assertEquals("3.7.2", properties.getProperty("asdf.python.version"));
        assertEquals("system", properties.getProperty("asdf.python.vendor"));
    }

    @Test
    void stripsLeadingVeePrefixFromNvmSource() throws Exception {
        Path file = tempDir.resolve("devtools-extension.properties");
        Files.write(
                file,
                ("transform.node.plain.source=nvm.node\n"
                                + "transform.node.plain.pattern=^v?(.*)$\n"
                                + "transform.node.plain.replacement=$1\n")
                        .getBytes(StandardCharsets.ISO_8859_1));

        Properties properties = new Properties();
        properties.setProperty("nvm.node", "v20.11.0");

        TransformConfiguration.load(file, NAMESPACES).apply(properties);

        assertEquals("20.11.0", properties.getProperty("nvm.node.plain"));
    }

    @Test
    void bareSourceIsResolvedInAlphabeticalNamespaceOrder() throws Exception {
        TransformConfiguration cfg =
                config(
                        "transform.java.major.source=java\n"
                                + "transform.java.major.pattern=^([0-9]+).*$\n"
                                + "transform.java.major.replacement=$1\n");
        Properties properties = new Properties();
        properties.setProperty("sdkman.java", "25-open");
        properties.setProperty("jenv.java", "17");

        cfg.apply(properties);

        assertEquals("17", properties.getProperty("jenv.java.major"));
        assertFalse(properties.containsKey("sdkman.java.major"));
    }

    @Test
    void explicitNamespaceIgnoresOrder() throws Exception {
        TransformConfiguration cfg =
                config(
                        "transform.java.major.source=sdkman.java\n"
                                + "transform.java.major.pattern=^([0-9]+).*$\n"
                                + "transform.java.major.replacement=$1\n");
        Properties properties = new Properties();
        properties.setProperty("sdkman.java", "25-open");
        properties.setProperty("jenv.java", "17");

        cfg.apply(properties);

        assertEquals("25", properties.getProperty("sdkman.java.major"));
    }

    @Test
    void namespaceOfAnyRegisteredParserIsRecognised() throws Exception {
        TransformConfiguration cfg =
                config(
                        "transform.x.source=foo.tool\n"
                                + "transform.x.pattern=^(\\\\d+).*$\n"
                                + "transform.x.replacement=$1\n"
                                + "transform.y.source=tool\n"
                                + "transform.y.pattern=^\\\\d+\\\\.(\\\\d+).*$\n"
                                + "transform.y.replacement=$1\n");
        Properties properties = new Properties();
        properties.setProperty("foo.tool", "3.9.1");

        cfg.apply(properties);

        assertEquals("3", properties.getProperty("foo.x"));
        assertEquals("9", properties.getProperty("foo.y"));
    }

    @Test
    void unknownNamespaceIsTreatedAsBareKeyAndReportedAsMissingInAnyNamespace() throws Exception {
        TransformConfiguration cfg =
                config(
                        "transform.x.source=bar.tool\n"
                                + "transform.x.pattern=.*\n"
                                + "transform.x.replacement=$0\n");
        Properties properties = new Properties();

        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> cfg.apply(properties));
        assertTrue(e.getMessage().contains("'bar.tool'"), e.getMessage());
        assertTrue(e.getMessage().contains("in any namespace"), e.getMessage());
    }

    private TransformConfiguration config(String content) throws Exception {
        Path file = tempDir.resolve("devtools-extension.properties");
        Files.write(file, content.getBytes(StandardCharsets.ISO_8859_1));
        return TransformConfiguration.load(file, NAMESPACES);
    }

    @Test
    void invalidGroupReferenceInReplacementFailsWithReadableMessage() throws Exception {
        TransformConfiguration cfg =
                config(
                        "transform.v.source=java\n"
                                + "transform.v.pattern=^([0-9]+).*$\n"
                                + "transform.v.replacement=$2\n");
        Properties properties = new Properties();
        properties.setProperty("sdkman.java", "25-open");

        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> cfg.apply(properties));
        assertTrue(e.getMessage().contains("'v'"), e.getMessage());
        assertTrue(e.getMessage().contains("$2"), e.getMessage());
        assertTrue(e.getMessage().contains("^([0-9]+).*$"), e.getMessage());
    }

    @Test
    void danglingDollarAndTrailingBackslashInReplacementFail() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("sdkman.java", "25-open");
        TransformConfiguration dollar =
                config(
                        "transform.v.source=java\n"
                                + "transform.v.pattern=^([0-9]+).*$\n"
                                + "transform.v.replacement=$\n");
        assertThrows(IllegalArgumentException.class, () -> dollar.apply(properties));
        TransformConfiguration backslash =
                config(
                        "transform.v.source=java\n"
                                + "transform.v.pattern=^([0-9]+).*$\n"
                                + "transform.v.replacement=$1\\\\\n");
        assertThrows(IllegalArgumentException.class, () -> backslash.apply(properties));
    }

    @Test
    void transformRunsAfterTransformProducingItsSourceEvenIfAlphabeticallyEarlier()
            throws Exception {
        // "a" depends on "z" (source = z), so z must run first.
        TransformConfiguration cfg =
                config(
                        "transform.a.source=sdkman.z\n"
                                + "transform.a.pattern=^([0-9]+).*$\n"
                                + "transform.a.replacement=$1\n"
                                + "transform.z.source=java\n"
                                + "transform.z.pattern=^([0-9.]+).*$\n"
                                + "transform.z.replacement=$1\n");
        Properties properties = new Properties();
        properties.setProperty("sdkman.java", "21.0.2-tem");

        cfg.apply(properties);

        assertEquals("21.0.2", properties.getProperty("sdkman.z"));
        assertEquals("21", properties.getProperty("sdkman.a"));
    }

    @Test
    void commandLineValueStillWinsOverTransformOutput() throws Exception {
        TransformConfiguration cfg =
                config(
                        "transform.major.source=java\n"
                                + "transform.major.pattern=^([0-9]+).*$\n"
                                + "transform.major.replacement=$1\n");
        Properties properties = new Properties();
        properties.setProperty("sdkman.java", "25-open");
        properties.setProperty("sdkman.major", "99");

        cfg.apply(properties);

        assertEquals("99", properties.getProperty("sdkman.major"));
    }

    @Test
    void circularTransformDependencyFailsWithReadableMessage() throws Exception {
        TransformConfiguration cfg =
                config(
                        "transform.a.source=sdkman.b\n"
                                + "transform.a.pattern=.*\n"
                                + "transform.b.source=sdkman.a\n"
                                + "transform.b.pattern=.*\n");
        Properties properties = new Properties();
        properties.setProperty("sdkman.a", "1");

        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> cfg.apply(properties));
        assertTrue(e.getMessage().contains("Circular"), e.getMessage());
        assertTrue(e.getMessage().contains("a") && e.getMessage().contains("b"));
    }

    @Test
    void invalidPatternFailsWithReadableMessage() throws Exception {
        TransformConfiguration cfg =
                config("transform.a.source=sdkman.java\ntransform.a.pattern=([0-9\n");
        Properties properties = new Properties();
        properties.setProperty("sdkman.java", "21");

        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> cfg.apply(properties));

        assertTrue(e.getMessage().contains("invalid pattern"), e.getMessage());
        assertTrue(e.getMessage().contains("'a'"), e.getMessage());
    }

    @Test
    void patternThatDoesNotMatchFailsWithReadableMessage() throws Exception {
        TransformConfiguration cfg =
                config("transform.a.source=sdkman.java\ntransform.a.pattern=^zzz\n");
        Properties properties = new Properties();
        properties.setProperty("sdkman.java", "21-tem");

        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> cfg.apply(properties));

        assertTrue(e.getMessage().contains("does not match"), e.getMessage());
        assertTrue(e.getMessage().contains("21-tem"), e.getMessage());
    }

    @Test
    void missingOrBlankRequiredPropertyFailsWithReadableMessage() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("sdkman.java", "21");

        TransformConfiguration noPattern = config("transform.a.source=sdkman.java\n");
        IllegalArgumentException missing =
                assertThrows(IllegalArgumentException.class, () -> noPattern.apply(properties));
        assertTrue(missing.getMessage().contains("transform.a.pattern"), missing.getMessage());

        TransformConfiguration blankSource =
                config("transform.a.source=   \ntransform.a.pattern=.*\n");
        IllegalArgumentException blank =
                assertThrows(IllegalArgumentException.class, () -> blankSource.apply(properties));
        assertTrue(blank.getMessage().contains("transform.a.source"), blank.getMessage());
    }

    @Test
    void transformWhoseSourceIsItsOwnTargetNameIsNotCycle() throws Exception {
        TransformConfiguration cfg =
                config(
                        "transform.java.source=java\n"
                                + "transform.java.pattern=^([0-9]+).*$\n"
                                + "transform.java.replacement=$1\n");
        Properties properties = new Properties();
        properties.setProperty("sdkman.java", "21-tem");

        cfg.apply(properties);

        assertEquals("21-tem", properties.getProperty("sdkman.java"));
    }

    @Test
    void entriesThatAreNotCompleteTransformFieldsAreIgnored() throws Exception {
        TransformConfiguration cfg =
                config(
                        "transform.flat=1\n"
                                + "transform.x.other=2\n"
                                + "transform..source=3\n"
                                + "dotenv.allow=FOO\n"
                                + "unrelated=4\n");

        assertTrue(cfg.apply(new Properties()).isEmpty());
    }
}

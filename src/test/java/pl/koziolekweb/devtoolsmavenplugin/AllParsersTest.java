package pl.koziolekweb.devtoolsmavenplugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.codehaus.plexus.PlexusContainer;
import org.eclipse.aether.RepositorySystemSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Happy path for every {@link ToolParser} at once: a reactor root that contains a valid file for
 * each supported tool. The parsers are discovered the way Sisu discovers them (through {@code
 * META-INF/sisu/javax.inject.Named}), so a parser missing from the index, or one without a fixture
 * file below, fails here.
 */
class AllParsersTest {

    private static final String SISU_INDEX = "/META-INF/sisu/javax.inject.Named";

    @TempDir Path root;

    private List<ToolParser> parsers;

    @BeforeEach
    void setUp() throws Exception {
        writeAllToolFiles();
        parsers = loadParsersFromSisuIndex();
    }

    /**
     * The Sisu index is generated from {@code @Named} at compile time; this guards the other half:
     * every concrete {@link ToolParser} on disk is annotated, so none is missing from the index.
     */
    @Test
    void sisuIndexListsExactlyTheConcreteToolParsersOnDisk() throws Exception {
        Path classesRoot =
                Paths.get(AllParsersTest.class.getResource(SISU_INDEX).toURI())
                        .getParent()
                        .getParent()
                        .getParent();
        Set<String> onDisk = new TreeSet<>();
        try (Stream<Path> files = Files.walk(classesRoot)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                String name = classesRoot.relativize(file).toString();
                if (!name.endsWith(".class")) {
                    continue;
                }
                Class<?> type =
                        Class.forName(
                                name.substring(0, name.length() - ".class".length())
                                        .replace(File.separatorChar, '.'));
                if (ToolParser.class.isAssignableFrom(type)
                        && !type.isInterface()
                        && !Modifier.isAbstract(type.getModifiers())) {
                    onDisk.add(type.getName());
                }
            }
        }
        Set<String> indexed = new TreeSet<>();
        for (ToolParser parser : parsers) {
            indexed.add(parser.getClass().getName());
        }

        assertFalse(onDisk.isEmpty());
        assertEquals(onDisk, indexed);
    }

    @Test
    void prefixesAreUniqueDottedAndUsedByEveryReturnedKey() {
        Set<String> seen = new HashSet<>();
        for (ToolParser parser : parsers) {
            String name = parser.getClass().getSimpleName();
            String prefix = parser.propertyPrefix();

            assertTrue(prefix.endsWith(".") && prefix.length() > 1, name + ": " + prefix);
            assertTrue(seen.add(prefix), name + " repeats prefix " + prefix);
            for (String key : parser.parse(root).keySet()) {
                assertTrue(key.startsWith(prefix), name + " returned " + key);
            }
        }
    }

    @Test
    void namespacesAreSortedAlphabeticallyWithoutDuplicates() {
        List<String> namespaces = DevtoolsLifecycleParticipant.namespacesOf(parsers);

        assertEquals(parsers.size(), namespaces.size());
        assertEquals(new ArrayList<>(new TreeSet<>(namespaces)), namespaces);
        assertEquals("asdf.", namespaces.get(0));
    }

    @Test
    void everyParserFindsItsFileAndReturnsPrefixedKeys() {
        for (ToolParser parser : parsers) {
            Map<String, String> values = parser.parse(root);

            assertFalse(
                    values.isEmpty(),
                    parser.getClass().getSimpleName() + " returned nothing for a valid file");
            for (String key : values.keySet()) {
                assertTrue(
                        key.indexOf('.') > 0,
                        parser.getClass().getSimpleName() + " returned unprefixed key " + key);
            }
        }
    }

    @Test
    void mergedResultOfAllParsersIsExactlyWhatTheFilesSay() {
        assertEquals(expectedProperties(), mergeLikeTheParticipant());
    }

    @Test
    void noKeyCollisionBetweenParsers() {
        int total = 0;
        for (ToolParser parser : parsers) {
            total += parser.parse(root).size();
        }

        // .nvmrc and .node-version are both handled by NvmParser, so no two parsers share a key.
        assertEquals(total, mergeLikeTheParticipant().size());
    }

    @Test
    @SuppressWarnings("java:S1874")
    void participantExposesEverythingAsUserPropertiesAndCommandLineWins() throws Exception {
        write(
                ".mvn/devtools-extension.properties",
                "dotenv.allow=APP_ENV,FOO\n"
                        + "transform.java.major.source=sdkman.java\n"
                        + "transform.java.major.pattern=^([0-9]+).*$\n"
                        + "transform.java.major.replacement=$1\n"
                        + "transform.node.plain.source=nvm.node\n"
                        + "transform.node.plain.pattern=^v?(.*)$\n"
                        + "transform.node.plain.replacement=$1\n");
        MavenExecutionRequest request = new DefaultMavenExecutionRequest();
        request.setMultiModuleProjectDirectory(root.toFile());
        request.getUserProperties().setProperty("sdkman.java", "21-temurin");
        MavenSession session =
                new MavenSession(
                        (PlexusContainer) null,
                        (RepositorySystemSession) null,
                        request,
                        (MavenExecutionResult) null);

        new DevtoolsLifecycleParticipant(parsers).afterSessionStart(session);

        Map<String, String> expected = expectedProperties();
        expected.put("sdkman.java", "21-temurin");
        expected.put("sdkman.java.major", "21");
        expected.put("nvm.node.plain", "20.11.0");
        Properties actual = request.getUserProperties();
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            assertEquals(entry.getValue(), actual.getProperty(entry.getKey()), entry.getKey());
        }
        assertEquals(expected.size(), actual.size());
    }

    @Test
    @SuppressWarnings("java:S1874")
    void unreadableEnvFileIsLoggedAndDoesNotStopTheOtherParsers() throws Exception {
        Path env = root.resolve(".env");
        Files.write(env, new byte[] {'A', '=', (byte) 0xC3, (byte) 0x28});
        MavenExecutionRequest request = new DefaultMavenExecutionRequest();
        request.setMultiModuleProjectDirectory(root.toFile());
        MavenSession session =
                new MavenSession(
                        (PlexusContainer) null,
                        (RepositorySystemSession) null,
                        request,
                        (MavenExecutionResult) null);

        try (LogCapture log = new LogCapture()) {
            new DevtoolsLifecycleParticipant(parsers).afterSessionStart(session);

            assertTrue(log.output().contains("Error reading file: " + env), log.output());
        }

        Properties actual = request.getUserProperties();
        Map<String, String> expected = expectedProperties();
        expected.remove("dotenv.APP_ENV");
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            assertEquals(entry.getValue(), actual.getProperty(entry.getKey()), entry.getKey());
        }
        assertEquals(expected.size(), actual.size());
    }

    /** Same merge as {@link DevtoolsLifecycleParticipant}: first value for a key wins. */
    private Map<String, String> mergeLikeTheParticipant() {
        Map<String, String> merged = new LinkedHashMap<>();
        for (ToolParser parser : parsers) {
            for (Map.Entry<String, String> entry : parser.parse(root).entrySet()) {
                merged.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }
        return merged;
    }

    private static Map<String, String> expectedProperties() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("sdkman.java", "25-open");
        m.put("sdkman.quarkus", "3.39.4");
        m.put("asdf.nodejs", "22.15.0");
        m.put("asdf.python", "3.7.2 2.7.15 system");
        m.put("mise.java", "temurin-21");
        m.put("mise.node", "20");
        m.put("jenv.java", "21.0.2");
        m.put("nvm.node", "v20.11.0");
        m.put(
                "mvnw.distributionUrl",
                "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/"
                        + "apache-maven-3.9.9-bin.zip");
        m.put("mvnw.wrapperVersion", "3.3.2");
        m.put("mvnw.maven", "3.9.9");
        m.put("jabba.jdk", "openjdk@1.11.0");
        m.put("proto.bun", "1.1.0");
        m.put("volta.node", "20.11.0");
        m.put("volta.npm", "10.2.4");
        m.put("devbox.jdk", "21");
        m.put("devbox.maven", "3.9");
        m.put("devcontainer.java", "21");
        m.put("devcontainer.node", "20");
        m.put("dotenv.APP_ENV", "dev");
        m.put("dotenv.FOO", "bar");
        m.put("pyenv.python", "3.12.1");
        m.put("rbenv.ruby", "3.3.0");
        m.put("tfenv.terraform", "1.7.0");
        m.put("bazelisk.bazel", "7.0.0");
        m.put("goenv.go", "1.22.0");
        return m;
    }

    private void writeAllToolFiles() throws Exception {
        write(".sdkmanrc", "# sdkman\njava=25-open\nquarkus=3.39.4\n");
        write(".tool-versions", "nodejs 22.15.0\npython 3.7.2 2.7.15 system\n");
        write("mise.toml", "[tools]\njava = \"temurin-21\"\nnode = \"20\"\n");
        write(".java-version", "21.0.2\n");
        write(".nvmrc", "v20.11.0\n");
        // Differs from .nvmrc on purpose: proves which of the two files wins.
        write(".node-version", "20.10.0\n");
        write(
                ".mvn/wrapper/maven-wrapper.properties",
                "wrapperVersion=3.3.2\n"
                        + "distributionUrl=https\\://repo.maven.apache.org/maven2/org/apache/maven/"
                        + "apache-maven/3.9.9/apache-maven-3.9.9-bin.zip\n");
        write(".jabbrc", "jdk: openjdk@1.11.0\n");
        write(".prototools", "bun = \"1.1.0\"\n\n[plugins]\nfoo = \"bar\"\n");
        write(
                "package.json",
                "{\"name\":\"x\",\"volta\":{\"node\":\"20.11.0\",\"npm\":\"10.2.4\"}}");
        write("devbox.json", "{\"packages\":[\"jdk@21\",\"maven@3.9\"]}");
        write(
                ".devcontainer/devcontainer.json",
                "{\n"
                        + "  // JSONC\n"
                        + "  \"features\": {\n"
                        + "    \"ghcr.io/devcontainers/features/java:1\": {\"version\": \"21\"},\n"
                        + "    \"ghcr.io/devcontainers/features/node:1\": {\"version\": \"20\",},\n"
                        + "  },\n"
                        + "}\n");
        write(".env", "APP_ENV=dev\nDB_PASSWORD=secret\n");
        write(".envrc", "export FOO=bar\nuse nix\n");
        write(".python-version", "3.12.1\n");
        write(".ruby-version", "3.3.0\n");
        write(".terraform-version", "1.7.0\n");
        write(".bazelversion", "7.0.0\n");
        write(".go-version", "1.22.0\n");
        write(".mvn/devtools-extension.properties", "dotenv.allow=APP_ENV,FOO\n");
    }

    private void write(String name, String content) throws Exception {
        Path path = root.resolve(name);
        Files.createDirectories(path.getParent());
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
    }

    private static List<ToolParser> loadParsersFromSisuIndex() throws Exception {
        List<ToolParser> result = new ArrayList<>();
        try (InputStream in = AllParsersTest.class.getResourceAsStream(SISU_INDEX);
                BufferedReader reader =
                        new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String className = line.trim();
                if (className.isEmpty()) {
                    continue;
                }
                Class<?> type = Class.forName(className);
                if (!ToolParser.class.isAssignableFrom(type)) {
                    continue; // e.g. DevtoolsLifecycleParticipant
                }
                java.lang.reflect.Constructor<?> constructor = type.getDeclaredConstructor();
                constructor.setAccessible(true);
                result.add((ToolParser) constructor.newInstance());
            }
        }
        return result;
    }
}

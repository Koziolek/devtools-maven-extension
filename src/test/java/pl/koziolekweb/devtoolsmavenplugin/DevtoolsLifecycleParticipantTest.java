package pl.koziolekweb.devtoolsmavenplugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.PlexusContainer;
import org.eclipse.aether.RepositorySystemSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The participant on its own, in the two layouts Maven runs it in: a single module project (no
 * multi-module directory, the base directory is the root) and a multi-module reactor (the root is
 * the multi-module directory, whatever module Maven was started in).
 */
class DevtoolsLifecycleParticipantTest {

    @TempDir Path root;

    private final List<ToolParser> parsers =
            Arrays.<ToolParser>asList(new SdkmanRcParser(), new NvmParser());

    private void write(Path dir, String relative, String content) throws Exception {
        Path file = dir.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
    }

    @SuppressWarnings("java:S1874")
    private MavenSession session(MavenExecutionRequest request, MavenProject... projects) {
        MavenSession session =
                new MavenSession(
                        (PlexusContainer) null,
                        (RepositorySystemSession) null,
                        request,
                        (MavenExecutionResult) null);
        session.setProjects(Arrays.asList(projects));
        return session;
    }

    private static MavenExecutionRequest singleModuleRequest(Path baseDirectory) {
        MavenExecutionRequest request = new DefaultMavenExecutionRequest();
        request.setBaseDirectory(baseDirectory.toFile());
        return request;
    }

    private static MavenExecutionRequest multiModuleRequest(Path root, Path startedIn) {
        MavenExecutionRequest request = new DefaultMavenExecutionRequest();
        request.setMultiModuleProjectDirectory(root.toFile());
        request.setBaseDirectory(startedIn.toFile());
        return request;
    }

    private static MavenProject projectWith(String name, String value) {
        MavenProject project = new MavenProject();
        if (name != null) {
            project.getProperties().setProperty(name, value);
        }
        return project;
    }

    /** A request whose only answers are the two getters {@code determineRoot} uses. */
    private static MavenExecutionRequest stubRequest(File multiModule, String baseDirectory) {
        return (MavenExecutionRequest)
                Proxy.newProxyInstance(
                        MavenExecutionRequest.class.getClassLoader(),
                        new Class<?>[] {MavenExecutionRequest.class},
                        (proxy, method, args) -> {
                            if ("getMultiModuleProjectDirectory".equals(method.getName())) {
                                return multiModule;
                            }
                            if ("getBaseDirectory".equals(method.getName())) {
                                return baseDirectory;
                            }
                            throw new UnsupportedOperationException(method.getName());
                        });
    }

    // --- determineRoot ---

    @Test
    void rootIsTheMultiModuleDirectoryWhenThereIsOne() {
        Path other = root.resolve("module-a");

        assertEquals(
                root, DevtoolsLifecycleParticipant.determineRoot(multiModuleRequest(root, other)));
    }

    @Test
    void multiModuleDirectoryWinsOverBaseDirectory() {
        MavenExecutionRequest request = stubRequest(root.toFile(), "/somewhere/else");

        assertEquals(root, DevtoolsLifecycleParticipant.determineRoot(request));
    }

    @Test
    void rootIsTheBaseDirectoryInSingleModuleProject() {
        assertEquals(root, DevtoolsLifecycleParticipant.determineRoot(singleModuleRequest(root)));
    }

    @Test
    void rootIsNormalisedAndAbsolute() {
        File withDots = root.resolve("module-a").resolve("..").toFile();

        assertEquals(root, DevtoolsLifecycleParticipant.determineRoot(stubRequest(withDots, null)));
        assertEquals(
                root,
                DevtoolsLifecycleParticipant.determineRoot(
                        stubRequest(null, root.resolve("x").resolve("..").toString())));
    }

    @Test
    void rootFallsBackToTheWorkingDirectoryWithoutAnyDirectory() {
        Path workingDirectory = Paths.get(".").toAbsolutePath().normalize();

        assertEquals(
                workingDirectory,
                DevtoolsLifecycleParticipant.determineRoot(new DefaultMavenExecutionRequest()));
        assertEquals(
                workingDirectory,
                DevtoolsLifecycleParticipant.determineRoot(stubRequest(null, null)));
        assertEquals(
                workingDirectory,
                DevtoolsLifecycleParticipant.determineRoot(stubRequest(null, "")));
        assertEquals(
                workingDirectory,
                DevtoolsLifecycleParticipant.determineRoot(stubRequest(null, "   ")));
    }

    // --- namespacesOf ---

    @Test
    void namespacesAreSortedAndUnique() {
        List<ToolParser> withDuplicate =
                Arrays.<ToolParser>asList(new NvmParser(), new SdkmanRcParser(), new NvmParser());

        assertEquals(
                Arrays.asList("nvm.", "sdkman."),
                DevtoolsLifecycleParticipant.namespacesOf(withDuplicate));
        assertTrue(DevtoolsLifecycleParticipant.namespacesOf(Collections.emptyList()).isEmpty());
    }

    // --- afterSessionStart: single module ---

    @Test
    void singleModuleReadsFilesFromTheBaseDirectory() throws Exception {
        write(root, ".sdkmanrc", "java=21-tem\n");
        write(root, ".nvmrc", "v20.11.0\n");
        MavenExecutionRequest request = singleModuleRequest(root);

        new DevtoolsLifecycleParticipant(parsers).afterSessionStart(session(request));

        Properties user = request.getUserProperties();
        assertEquals("21-tem", user.getProperty("sdkman.java"));
        assertEquals("v20.11.0", user.getProperty("nvm.node"));
        assertEquals(2, user.size());
    }

    @Test
    void singleModuleAppliesTheTransformConfiguration() throws Exception {
        write(root, ".sdkmanrc", "java=21.0.2-tem\n");
        write(
                root,
                ".mvn/devtools-extension.properties",
                "transform.java.major.source=sdkman.java\n"
                        + "transform.java.major.pattern=^([0-9]+).*$\n"
                        + "transform.java.major.replacement=$1\n");
        MavenExecutionRequest request = singleModuleRequest(root);

        new DevtoolsLifecycleParticipant(parsers).afterSessionStart(session(request));

        assertEquals("21", request.getUserProperties().getProperty("sdkman.java.major"));
    }

    @Test
    void withoutAnyFileNothingIsSet() throws Exception {
        MavenExecutionRequest request = singleModuleRequest(root);

        new DevtoolsLifecycleParticipant(parsers).afterSessionStart(session(request));

        assertTrue(request.getUserProperties().isEmpty());
    }

    @Test
    void commandLinePropertyWinsOverTheFile() throws Exception {
        write(root, ".sdkmanrc", "java=21-tem\n");
        MavenExecutionRequest request = singleModuleRequest(root);
        request.getUserProperties().setProperty("sdkman.java", "17-cli");

        new DevtoolsLifecycleParticipant(parsers).afterSessionStart(session(request));

        assertEquals("17-cli", request.getUserProperties().getProperty("sdkman.java"));
    }

    // --- afterSessionStart: multi module ---

    @Test
    void multiModuleReadsFilesFromTheReactorRootNotFromTheModuleItWasStartedIn() throws Exception {
        Path module = root.resolve("module-a");
        write(root, ".sdkmanrc", "java=21-root\n");
        write(module, ".sdkmanrc", "java=8-module\nmaven=3.9.9\n");
        MavenExecutionRequest request = multiModuleRequest(root, module);

        new DevtoolsLifecycleParticipant(parsers).afterSessionStart(session(request));

        Properties user = request.getUserProperties();
        assertEquals("21-root", user.getProperty("sdkman.java"));
        assertNull(user.getProperty("sdkman.maven"));
        assertEquals(1, user.size());
    }

    @Test
    void multiModuleUsesTheConfigurationOfTheReactorRoot() throws Exception {
        Path module = root.resolve("module-a");
        write(root, ".sdkmanrc", "java=21.0.2-tem\n");
        write(
                root,
                ".mvn/devtools-extension.properties",
                "transform.java.major.source=sdkman.java\n"
                        + "transform.java.major.pattern=^([0-9]+).*$\n"
                        + "transform.java.major.replacement=$1\n");
        write(
                module,
                ".mvn/devtools-extension.properties",
                "transform.java.major.source=sdkman.java\n"
                        + "transform.java.major.pattern=^[0-9]+\\\\.([0-9]+).*$\n"
                        + "transform.java.major.replacement=$1\n");
        MavenExecutionRequest request = multiModuleRequest(root, module);

        new DevtoolsLifecycleParticipant(parsers).afterSessionStart(session(request));

        assertEquals("21", request.getUserProperties().getProperty("sdkman.java.major"));
    }

    @Test
    void multiModuleWithoutFilesInTheRootIgnoresFilesInModules() throws Exception {
        Path module = root.resolve("module-a");
        write(module, ".sdkmanrc", "java=8-module\n");
        MavenExecutionRequest request = multiModuleRequest(root, module);

        new DevtoolsLifecycleParticipant(parsers).afterSessionStart(session(request));

        assertTrue(request.getUserProperties().isEmpty());
    }

    // --- afterSessionStart: errors ---

    @Test
    void brokenTransformConfigurationFailsTheBuildWithTheRootInTheMessage() throws Exception {
        write(
                root,
                ".mvn/devtools-extension.properties",
                "transform.java.major.source=sdkman.java\n"
                        + "transform.java.major.pattern=^([0-9]+).*$\n");
        MavenExecutionRequest request = singleModuleRequest(root);
        DevtoolsLifecycleParticipant participant = new DevtoolsLifecycleParticipant(parsers);
        MavenSession session = session(request);

        MavenExecutionException error =
                assertThrows(
                        MavenExecutionException.class,
                        () -> participant.afterSessionStart(session));

        assertTrue(error.getMessage().contains(root.toString()), error.getMessage());
        assertTrue(error.getCause() instanceof IllegalArgumentException, "" + error.getCause());
    }

    @Test
    void transformWithMissingSourceFailsTheBuild() throws Exception {
        write(
                root,
                ".mvn/devtools-extension.properties",
                "transform.java.major.source=sdkman.java\n"
                        + "transform.java.major.pattern=^([0-9]+).*$\n"
                        + "transform.java.major.replacement=$1\n");
        MavenExecutionRequest request = singleModuleRequest(root);
        DevtoolsLifecycleParticipant participant = new DevtoolsLifecycleParticipant(parsers);
        MavenSession session = session(request);

        assertThrows(MavenExecutionException.class, () -> participant.afterSessionStart(session));
    }

    // --- afterProjectsRead ---

    @Test
    void singleModuleProjectGetsTheResolvedProperties() throws Exception {
        write(root, ".sdkmanrc", "java=21-tem\n");
        write(
                root,
                ".mvn/devtools-extension.properties",
                "transform.java.major.source=sdkman.java\n"
                        + "transform.java.major.pattern=^([0-9]+).*$\n"
                        + "transform.java.major.replacement=$1\n");
        MavenExecutionRequest request = singleModuleRequest(root);
        request.getUserProperties().setProperty("unrelated", "cli");
        MavenProject project = projectWith(null, null);
        MavenSession session = session(request, project);
        DevtoolsLifecycleParticipant participant = new DevtoolsLifecycleParticipant(parsers);

        participant.afterSessionStart(session);
        participant.afterProjectsRead(session);

        Properties properties = project.getProperties();
        assertEquals("21-tem", properties.getProperty("sdkman.java"));
        assertEquals("21", properties.getProperty("sdkman.java.major"));
        assertNull(properties.getProperty("unrelated"), "only managed properties are mirrored");
        assertEquals(2, properties.size());
    }

    @Test
    void everyModuleOfTheReactorGetsTheResolvedPropertiesAndKeepsItsOwnDeclarations()
            throws Exception {
        write(root, ".sdkmanrc", "java=21-tem\n");
        write(root, ".nvmrc", "v20.11.0\n");
        MavenExecutionRequest request = multiModuleRequest(root, root);
        MavenProject parent = projectWith(null, null);
        MavenProject plain = projectWith("custom", "kept");
        MavenProject declaring = projectWith("sdkman.java", "11-pom");
        MavenSession session = session(request, parent, plain, declaring);
        DevtoolsLifecycleParticipant participant = new DevtoolsLifecycleParticipant(parsers);

        participant.afterSessionStart(session);
        participant.afterProjectsRead(session);

        for (MavenProject project : Arrays.asList(parent, plain, declaring)) {
            assertEquals("v20.11.0", project.getProperties().getProperty("nvm.node"));
        }
        assertEquals("21-tem", parent.getProperties().getProperty("sdkman.java"));
        assertEquals("21-tem", plain.getProperties().getProperty("sdkman.java"));
        assertEquals("kept", plain.getProperties().getProperty("custom"));
        assertEquals("11-pom", declaring.getProperties().getProperty("sdkman.java"));
    }

    @Test
    void commandLineValueIsWhatTheProjectsGet() throws Exception {
        write(root, ".sdkmanrc", "java=21-tem\n");
        MavenExecutionRequest request = multiModuleRequest(root, root);
        request.getUserProperties().setProperty("sdkman.java", "17-cli");
        MavenProject first = projectWith(null, null);
        MavenProject second = projectWith(null, null);
        MavenSession session = session(request, first, second);
        DevtoolsLifecycleParticipant participant = new DevtoolsLifecycleParticipant(parsers);

        participant.afterSessionStart(session);
        participant.afterProjectsRead(session);

        assertEquals("17-cli", first.getProperties().getProperty("sdkman.java"));
        assertEquals("17-cli", second.getProperties().getProperty("sdkman.java"));
    }

    @Test
    void propertyRemovedFromTheRequestIsNotMirrored() throws Exception {
        write(root, ".sdkmanrc", "java=21-tem\nmaven=3.9.9\n");
        MavenExecutionRequest request = singleModuleRequest(root);
        MavenProject project = projectWith(null, null);
        MavenSession session = session(request, project);
        DevtoolsLifecycleParticipant participant = new DevtoolsLifecycleParticipant(parsers);

        participant.afterSessionStart(session);
        request.getUserProperties().remove("sdkman.maven");
        participant.afterProjectsRead(session);

        assertEquals("21-tem", project.getProperties().getProperty("sdkman.java"));
        assertNull(project.getProperties().getProperty("sdkman.maven"));
    }

    @Test
    void afterProjectsReadWithoutAnEarlierSessionStartChangesNothing() {
        MavenExecutionRequest request = singleModuleRequest(root);
        request.getUserProperties().setProperty("sdkman.java", "17-cli");
        MavenProject project = projectWith(null, null);

        new DevtoolsLifecycleParticipant(parsers).afterProjectsRead(session(request, project));

        assertTrue(project.getProperties().isEmpty());
    }

    @Test
    void afterProjectsReadWithoutProjectsIsHarmless() throws Exception {
        write(root, ".sdkmanrc", "java=21-tem\n");
        MavenExecutionRequest request = singleModuleRequest(root);
        MavenSession session = session(request);
        DevtoolsLifecycleParticipant participant = new DevtoolsLifecycleParticipant(parsers);

        participant.afterSessionStart(session);
        participant.afterProjectsRead(session);

        assertEquals("21-tem", request.getUserProperties().getProperty("sdkman.java"));
    }

    @Test
    void secondSessionReplacesTheManagedPropertyNames() throws Exception {
        write(root, ".sdkmanrc", "java=21-tem\n");
        DevtoolsLifecycleParticipant participant = new DevtoolsLifecycleParticipant(parsers);
        participant.afterSessionStart(session(singleModuleRequest(root)));

        Path other = root.resolve("other");
        write(other, ".nvmrc", "v18.0.0\n");
        MavenExecutionRequest request = singleModuleRequest(other);
        MavenProject project = projectWith(null, null);
        MavenSession session = session(request, project);
        participant.afterSessionStart(session);
        participant.afterProjectsRead(session);

        Map<Object, Object> mirrored = project.getProperties();
        assertEquals("v18.0.0", mirrored.get("nvm.node"));
        assertNull(mirrored.get("sdkman.java"));
    }
}

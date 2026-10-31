package pl.koziolekweb.devtoolsmavenplugin;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.codehaus.plexus.PlexusContainer;
import org.eclipse.aether.RepositorySystemSession;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

/**
 * End to end: {@link DevtoolsLifecycleParticipant#afterSessionStart} with every parser registered,
 * on a root with all tool files plus a small transform configuration ("full") and on an empty
 * directory ("empty"). The request and session are built per call, as Maven does per build; the
 * session is created with {@code null} for the parts {@code afterSessionStart} does not touch.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class ParticipantBenchmark {

    @Param({"full", "empty"})
    public String root;

    private DevtoolsLifecycleParticipant participant;
    private Path rootDir;

    @Setup(Level.Trial)
    public void setUp() throws IOException {
        participant = new DevtoolsLifecycleParticipant(BenchmarkFixture.allParsers());
        rootDir = BenchmarkFixture.newTempDir();
        if ("full".equals(root)) {
            BenchmarkFixture.writeAllToolFiles(rootDir);
            BenchmarkFixture.write(
                    rootDir,
                    ".mvn/devtools-extension.properties",
                    "dotenv.allow=APP_ENV,FOO\n"
                            + "transform.java.major.source=sdkman.java\n"
                            + "transform.java.major.pattern=^([0-9]+).*$\n"
                            + "transform.java.major.replacement=$1\n"
                            + "transform.node.plain.source=nvm.node\n"
                            + "transform.node.plain.pattern=^v?(.*)$\n"
                            + "transform.node.plain.replacement=$1\n");
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() throws IOException {
        BenchmarkFixture.deleteRecursively(rootDir);
    }

    @Benchmark
    @SuppressWarnings("java:S1874") // SonarQube disabled: benchmark uses deprecated constructor for minimal setup
    public Properties afterSessionStart() throws MavenExecutionException {
        MavenExecutionRequest request = new DefaultMavenExecutionRequest();
        request.setMultiModuleProjectDirectory(rootDir.toFile());
        MavenSession session =
                new MavenSession(
                        (PlexusContainer) null,
                        (RepositorySystemSession) null,
                        request,
                        (MavenExecutionResult) null);
        participant.afterSessionStart(session);
        return request.getUserProperties();
    }
}

package pl.koziolekweb.devtoolsmavenplugin;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
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
 * Cost of {@link TransformConfiguration} for 1, 50 and 500 transformations: {@code apply} on an
 * already loaded configuration, and {@code load} plus {@code apply} (which also reads the file).
 * Every transformation compiles its regex on each {@code apply}, so this shows whether that
 * matters.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class TransformBenchmark {

    @Param({"1", "50", "500"})
    public int transformations;

    private Path root;
    private Path configFile;
    private java.util.List<String> namespaces;
    private TransformConfiguration configuration;

    @Setup(Level.Trial)
    public void setUp() throws IOException {
        root = BenchmarkFixture.newTempDir();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < transformations; i++) {
            sb.append("transform.java.major").append(i).append(".source=sdkman.java\n");
            sb.append("transform.java.major").append(i).append(".pattern=^([0-9]+).*$\n");
            sb.append("transform.java.major").append(i).append(".replacement=$1\n");
        }
        BenchmarkFixture.write(root, "devtools-extension.properties", sb.toString());
        configFile = root.resolve("devtools-extension.properties");
        namespaces = DevtoolsLifecycleParticipant.namespacesOf(BenchmarkFixture.allParsers());
        configuration = TransformConfiguration.load(configFile, namespaces);
    }

    @TearDown(Level.Trial)
    public void tearDown() throws IOException {
        BenchmarkFixture.deleteRecursively(root);
    }

    @Benchmark
    public Properties apply() {
        // Fresh properties each call: apply() uses putIfAbsent, so reusing them would turn
        // every call after the first into a no-op write.
        Properties properties = new Properties();
        properties.setProperty("sdkman.java", "25-open");
        configuration.apply(properties);
        return properties;
    }

    @Benchmark
    public Properties loadAndApply() throws IOException {
        Properties properties = new Properties();
        properties.setProperty("sdkman.java", "25-open");
        TransformConfiguration.load(configFile, namespaces).apply(properties);
        return properties;
    }
}

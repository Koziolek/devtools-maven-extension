package pl.koziolekweb.devtoolsmavenplugin;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
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
 * Steady-state cost of one {@link ToolParser#parse(Path)} call, for every parser, with its file
 * present and with the file absent. "Missing" is the common case: most projects have only a few of
 * the supported files, yet every parser still runs on every Maven invocation.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class SingleParserBenchmark {

    @Param({
        "SdkmanRcParser",
        "AsdfToolVersionsParser",
        "MiseParser",
        "JenvParser",
        "NvmParser",
        "MavenWrapperParser",
        "JabbaParser",
        "ProtoParser",
        "VoltaParser",
        "DevboxParser",
        "DevcontainerParser",
        "DotenvParser",
        "PyenvParser",
        "RbenvParser",
        "TfenvParser",
        "BazeliskParser",
        "GoenvParser"
    })
    public String parser;

    @Param({"present", "missing"})
    public String file;

    private ToolParser instance;
    private Path root;

    @Setup(Level.Trial)
    public void setUp() throws IOException {
        instance = BenchmarkFixture.newParser(parser);
        root = BenchmarkFixture.newTempDir();
        if ("present".equals(file)) {
            BenchmarkFixture.writeAllToolFiles(root);
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() throws IOException {
        BenchmarkFixture.deleteRecursively(root);
    }

    @Benchmark
    public Map<String, String> parse() {
        return instance.parse(root);
    }
}

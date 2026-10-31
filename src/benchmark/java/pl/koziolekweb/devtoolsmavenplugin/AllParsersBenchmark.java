package pl.koziolekweb.devtoolsmavenplugin;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
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
 * Steady-state cost of running every parser one after another, merged like {@link
 * DevtoolsLifecycleParticipant} does, on a root with all tool files ("full") and on an empty
 * directory ("empty").
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class AllParsersBenchmark {

    @Param({"full", "empty"})
    public String root;

    private List<ToolParser> parsers;
    private Path rootDir;

    @Setup(Level.Trial)
    public void setUp() throws IOException {
        parsers = BenchmarkFixture.allParsers();
        rootDir = BenchmarkFixture.newTempDir();
        if ("full".equals(root)) {
            BenchmarkFixture.writeAllToolFiles(rootDir);
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() throws IOException {
        BenchmarkFixture.deleteRecursively(rootDir);
    }

    @Benchmark
    public Map<String, String> parseAll() {
        Map<String, String> merged = new LinkedHashMap<>();
        for (ToolParser parser : parsers) {
            for (Map.Entry<String, String> entry : parser.parse(rootDir).entrySet()) {
                merged.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }
        return merged;
    }
}

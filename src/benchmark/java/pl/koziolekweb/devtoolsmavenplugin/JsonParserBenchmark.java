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
 * Warm cost of the Moshi-based parsers on a small file and on a roughly 1 MB one. These parsers
 * read the whole document to use one section of it ({@code volta}, {@code packages}, {@code
 * features}), which matters for a big {@code package.json}. The cold first call is measured
 * separately in {@link ColdStartBenchmark}.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class JsonParserBenchmark {

    /** About 20 bytes per entry, so this is roughly 1 MB. */
    private static final int LARGE_FILLER = 50_000;

    @Param({"VoltaParser", "DevboxParser", "DevcontainerParser"})
    public String parser;

    @Param({"small", "large"})
    public String size;

    private ToolParser instance;
    private Path root;

    @Setup(Level.Trial)
    public void setUp() throws IOException {
        instance = BenchmarkFixture.newParser(parser);
        root = BenchmarkFixture.newTempDir();
        int filler = "large".equals(size) ? LARGE_FILLER : 0;
        switch (parser) {
            case "VoltaParser":
                BenchmarkFixture.write(root, "package.json", BenchmarkFixture.voltaJson(filler));
                break;
            case "DevboxParser":
                BenchmarkFixture.write(root, "devbox.json", BenchmarkFixture.devboxJson(filler));
                break;
            case "DevcontainerParser":
                BenchmarkFixture.write(
                        root,
                        ".devcontainer/devcontainer.json",
                        BenchmarkFixture.devcontainerJson(filler));
                break;
            default:
                throw new IllegalArgumentException("Not a JSON parser: " + parser);
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

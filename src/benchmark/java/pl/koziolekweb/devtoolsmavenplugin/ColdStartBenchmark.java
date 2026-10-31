package pl.koziolekweb.devtoolsmavenplugin;

import java.io.IOException;
import java.nio.file.Path;
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
 * Cost of the first {@code parse} call in a fresh JVM, which is what a Maven build actually pays:
 * the JVM lives for seconds, so nothing is JIT-warm. Each measurement is a single shot in its own
 * fork. This is where Moshi (and with it okio and the Kotlin runtime) shows its class-loading
 * cost. Parser objects are created in {@code @Setup}, so only the {@code parse} call is timed;
 * classes first touched inside {@code parse} (Moshi, regex, file APIs) are included.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 0)
@Measurement(iterations = 1)
@Fork(10)
public class ColdStartBenchmark {

    /** {@code AllParsers} runs every parser in sequence, like the lifecycle participant. */
    @Param({
        "SdkmanRcParser",
        "MiseParser",
        "ProtoParser",
        "VoltaParser",
        "DevboxParser",
        "DevcontainerParser",
        "AllParsers"
    })
    public String parser;

    /** {@code full}: every tool file present; {@code empty}: a directory with none of them. */
    @Param({"full", "empty"})
    public String files;

    private List<ToolParser> parsers;
    private Path root;

    @Setup(Level.Trial)
    public void setUp() throws IOException {
        parsers =
                "AllParsers".equals(parser)
                        ? BenchmarkFixture.allParsers()
                        : java.util.Collections.singletonList(BenchmarkFixture.newParser(parser));
        root = BenchmarkFixture.newTempDir();
        if ("full".equals(files)) {
            BenchmarkFixture.writeAllToolFiles(root);
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() throws IOException {
        BenchmarkFixture.deleteRecursively(root);
    }

    @Benchmark
    public int parseOnce() {
        int entries = 0;
        for (ToolParser toolParser : parsers) {
            Map<String, String> values = toolParser.parse(root);
            entries += values.size();
        }
        return entries;
    }
}

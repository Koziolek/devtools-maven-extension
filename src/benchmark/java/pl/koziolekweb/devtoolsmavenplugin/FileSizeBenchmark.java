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
 * How the line-oriented parsers scale with the size of their file. {@code WithLineReader} reads the
 * whole file into memory, so this shows whether that is a problem for realistic and for absurdly
 * large files. {@code NvmParser} is the interesting one: it needs only the first line, so any
 * growth with the line count is pure reading overhead.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class FileSizeBenchmark {

    @Param({
        "SdkmanRcParser",
        "AsdfToolVersionsParser",
        "DotenvParser",
        "MiseParser",
        "ProtoParser",
        "NvmParser"
    })
    public String parser;

    @Param({"10", "1000", "100000"})
    public int lines;

    private ToolParser instance;
    private Path root;

    @Setup(Level.Trial)
    public void setUp() throws IOException {
        instance = BenchmarkFixture.newParser(parser);
        root = BenchmarkFixture.newTempDir();
        BenchmarkFixture.write(root, fileName(parser), content(parser, lines));
        // Dotenv exports nothing by default; "*" keeps measuring the full parse.
        BenchmarkFixture.write(root, ".mvn/devtools-extension.properties", "dotenv.allow=*\n");
    }

    @TearDown(Level.Trial)
    public void tearDown() throws IOException {
        BenchmarkFixture.deleteRecursively(root);
    }

    @Benchmark
    public Map<String, String> parse() {
        return instance.parse(root);
    }

    private static String fileName(String parser) {
        switch (parser) {
            case "SdkmanRcParser":
                return ".sdkmanrc";
            case "AsdfToolVersionsParser":
                return ".tool-versions";
            case "DotenvParser":
                return ".env";
            case "MiseParser":
                return "mise.toml";
            case "ProtoParser":
                return ".prototools";
            case "NvmParser":
                return ".nvmrc";
            default:
                throw new IllegalArgumentException("No file for " + parser);
        }
    }

    private static String content(String parser, int lines) {
        StringBuilder sb = new StringBuilder(lines * 24);
        if ("MiseParser".equals(parser)) {
            sb.append("[tools]\n");
        } else if ("NvmParser".equals(parser)) {
            sb.append("v20.11.0\n");
        }
        for (int i = 0; i < lines; i++) {
            sb.append(line(parser, i)).append('\n');
        }
        return sb.toString();
    }

    private static String line(String parser, int i) {
        switch (parser) {
            case "SdkmanRcParser":
                return "tool" + i + "=1." + i + ".0";
            case "AsdfToolVersionsParser":
                return "tool" + i + " 1." + i + ".0";
            case "DotenvParser":
                return "KEY_" + i + "=value" + i;
            case "MiseParser":
            case "ProtoParser":
                return "tool" + i + " = \"1." + i + ".0\"";
            case "NvmParser":
                return "# comment " + i;
            default:
                throw new IllegalArgumentException("No line format for " + parser);
        }
    }
}

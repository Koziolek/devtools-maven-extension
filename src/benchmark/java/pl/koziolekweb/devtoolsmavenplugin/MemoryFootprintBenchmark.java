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
 * Memory footprint of every parser as a function of the size of its file: 10 kB, 100 kB, 1 MB and
 * 10 MB. The number to read is the secondary metric {@code gc.alloc.rate.norm} (bytes allocated per
 * {@code parse} call), which {@link BenchmarkRunnerTest} adds with JMH's {@code GCProfiler}; the
 * time is only a by-product. Dividing it by the file size shows whether the cost is linear (a
 * constant number of bytes per byte of file) or not.
 *
 * <p>The file is a valid one with the usual content repeated until it reaches the requested size
 * (key/value parsers: one entry per line; single-version parsers: the version, then comment lines;
 * JSON parsers: the usual document plus filler members). It is allocation, not the retained heap:
 * the garbage a parser leaves behind counts too.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
public class MemoryFootprintBenchmark {

    private static final int KILOBYTE = 1024;

    @Param({
        "SdkmanRcParser",
        "AsdfToolVersionsParser",
        "DotenvParser",
        "MiseParser",
        "ProtoParser",
        "JabbaParser",
        "MavenWrapperParser",
        "NvmParser",
        "JenvParser",
        "PyenvParser",
        "RbenvParser",
        "TfenvParser",
        "BazeliskParser",
        "GoenvParser",
        "VoltaParser",
        "DevboxParser",
        "DevcontainerParser"
    })
    public String parser;

    @Param({"10", "100", "1000", "10000"})
    public int sizeKb;

    private ToolParser instance;
    private Path root;

    @Setup(Level.Trial)
    public void setUp() throws IOException {
        instance = BenchmarkFixture.newParser(parser);
        root = BenchmarkFixture.newTempDir();
        int bytes = sizeKb * KILOBYTE;
        BenchmarkFixture.write(root, fileName(parser), content(parser, bytes));
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
            case "JabbaParser":
                return ".jabbrc";
            case "MavenWrapperParser":
                return ".mvn/wrapper/maven-wrapper.properties";
            case "NvmParser":
                return ".nvmrc";
            case "JenvParser":
                return ".java-version";
            case "PyenvParser":
                return ".python-version";
            case "RbenvParser":
                return ".ruby-version";
            case "TfenvParser":
                return ".terraform-version";
            case "BazeliskParser":
                return ".bazelversion";
            case "GoenvParser":
                return ".go-version";
            case "VoltaParser":
                return "package.json";
            case "DevboxParser":
                return "devbox.json";
            case "DevcontainerParser":
                return ".devcontainer/devcontainer.json";
            default:
                throw new IllegalArgumentException("No file for " + parser);
        }
    }

    private static String content(String parser, int bytes) {
        switch (parser) {
            case "VoltaParser":
                return BenchmarkFixture.voltaJson(fillerCount(bytes));
            case "DevboxParser":
                return BenchmarkFixture.devboxJson(fillerCount(bytes));
            case "DevcontainerParser":
                return BenchmarkFixture.devcontainerJson(fillerCount(bytes));
            default:
                return lines(parser, bytes);
        }
    }

    /** Number of filler members that make the whole JSON document about {@code bytes} long. */
    private static int fillerCount(int bytes) {
        int estimate = bytes / 20;
        int measured = BenchmarkFixture.voltaJson(estimate).length();
        return (int) ((long) estimate * bytes / measured);
    }

    private static String lines(String parser, int bytes) {
        StringBuilder sb = new StringBuilder(bytes + 64);
        sb.append(header(parser));
        for (int i = 0; sb.length() < bytes; i++) {
            sb.append(line(parser, i)).append('\n');
        }
        return sb.toString();
    }

    private static String header(String parser) {
        switch (parser) {
            case "MiseParser":
                return "[tools]\n";
            case "MavenWrapperParser":
                return "distributionUrl=https\\://repo.maven.apache.org/maven2/org/apache/maven/"
                        + "apache-maven/3.9.9/apache-maven-3.9.9-bin.zip\n";
            case "NvmParser":
                return "v20.11.0\n";
            case "JenvParser":
            case "PyenvParser":
            case "RbenvParser":
            case "TfenvParser":
            case "BazeliskParser":
            case "GoenvParser":
                return "1.2.3\n";
            default:
                return "";
        }
    }

    private static String line(String parser, int i) {
        switch (parser) {
            case "SdkmanRcParser":
            case "MavenWrapperParser":
                return "tool" + i + "=1." + i + ".0";
            case "AsdfToolVersionsParser":
                return "tool" + i + " 1." + i + ".0";
            case "DotenvParser":
                return "KEY_" + i + "=value" + i;
            case "MiseParser":
            case "ProtoParser":
                return "tool" + i + " = \"1." + i + ".0\"";
            case "JabbaParser":
                return "tool" + i + ": 1." + i + ".0";
            default:
                // single-version files: only the first line counts, the rest is read and dropped
                return "# comment " + i;
        }
    }
}

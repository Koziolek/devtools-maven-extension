package pl.koziolekweb.devtoolsmavenplugin;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * Starts the JMH benchmarks from JUnit, so they run from the IDE or from Maven without a separate
 * launcher. JMH still forks its own JVMs; this test only builds the options and waits.
 *
 * <p>Tagged {@code benchmark}: a full run takes about ten minutes. Run it with {@code mvn
 * -Pbenchmarks test -Dtest=BenchmarkRunnerTest}, or only the tagged test with {@code mvn
 * -Pbenchmarks test-compile surefire:test@jmh}.
 *
 * <p>The JaCoCo agent that surefire puts on the test JVM is not passed on to the forks: it
 * instruments every class on load and inflates the cold start benchmarks two to three times.
 *
 * <p>JMH's {@code GCProfiler} is always on, so every result also carries the allocation per
 * operation ({@code gc.alloc.rate.norm}), which {@link MemoryFootprintBenchmark} is about.
 *
 * <p>System properties: {@code jmh.include} is a regular expression selecting benchmarks (default
 * all), {@code jmh.result} is the path of the JSON result file.
 */
@Tag("benchmark")
@SuppressWarnings("java:S2699") // SonarQube disabled: this is a JMH benchmark runner, not a regular test
class BenchmarkRunnerTest {

    @Test
    void runBenchmarks() throws RunnerException {
        Options options =
                new OptionsBuilder()
                        .include(System.getProperty("jmh.include", ".*"))
                        .jvmArgs(forkJvmArgs())
                        .addProfiler(GCProfiler.class)
                        .resultFormat(ResultFormatType.JSON)
                        .result(System.getProperty("jmh.result", "target/jmh-result.json"))
                        .build();

        new Runner(options).run();
    }

    /** Arguments of this JVM without {@code -javaagent}, which JMH would otherwise copy. */
    private static String[] forkJvmArgs() {
        List<String> args = new ArrayList<>();
        for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (!arg.startsWith("-javaagent:")) {
                args.add(arg);
            }
        }
        return args.toArray(new String[0]);
    }
}

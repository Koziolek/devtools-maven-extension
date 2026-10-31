package pl.koziolekweb.devtoolsmavenplugin;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Captures what slf4j-simple writes while it is open. slf4j-simple resolves {@code System.err} on
 * every log call, so swapping the stream is enough; no appender is needed. Not thread safe — use it
 * from a single test thread and always in try-with-resources.
 */
final class LogCapture implements AutoCloseable {

    private final PrintStream original = System.err;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    LogCapture() {
        System.setErr(new PrintStream(buffer, true));
    }

    /** Everything logged since this capture was opened, including stack traces. */
    String output() {
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
        System.setErr(original);
    }
}

package pl.koziolekweb.devtoolsmavenplugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared setup for the JMH benchmarks: temp directories, a reactor root with a valid file for
 * every supported tool, and the {@link ToolParser}s discovered the way Sisu discovers them.
 */
final class BenchmarkFixture {

    private static final String SISU_INDEX = "/META-INF/sisu/javax.inject.Named";
    private static final String PACKAGE = "pl.koziolekweb.devtoolsmavenplugin.";

    private BenchmarkFixture() {}

    static Path newTempDir() throws IOException {
        return Files.createTempDirectory("devtools-jmh");
    }

    static void deleteRecursively(Path dir) throws IOException {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        Files.walkFileTree(
                dir,
                new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                            throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path d, IOException exc)
                            throws IOException {
                        Files.delete(d);
                        return FileVisitResult.CONTINUE;
                    }
                });
    }

    static void write(Path root, String name, String content) throws IOException {
        Path path = root.resolve(name);
        Files.createDirectories(path.getParent());
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
    }

    /** Instantiates one parser by simple class name, e.g. {@code "VoltaParser"}. */
    static ToolParser newParser(String simpleName) {
        try {
            return instantiate(Class.forName(PACKAGE + simpleName));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot create parser " + simpleName, e);
        }
    }

    /** Every {@link ToolParser} listed in the Sisu index, in index order. */
    static List<ToolParser> allParsers() {
        List<ToolParser> result = new ArrayList<>();
        try (InputStream in = BenchmarkFixture.class.getResourceAsStream(SISU_INDEX);
                BufferedReader reader =
                        new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String className = line.trim();
                if (className.isEmpty()) {
                    continue;
                }
                Class<?> type = Class.forName(className);
                if (ToolParser.class.isAssignableFrom(type)) {
                    result.add(instantiate(type));
                }
            }
        } catch (IOException | ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot load parsers from " + SISU_INDEX, e);
        }
        return result;
    }

    private static ToolParser instantiate(Class<?> type) throws ReflectiveOperationException {
        Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return (ToolParser) constructor.newInstance();
    }

    /** A valid file for each of the supported tools; same content as {@code AllParsersTest}. */
    static void writeAllToolFiles(Path root) throws IOException {
        write(root, ".sdkmanrc", "# sdkman\njava=25-open\nquarkus=3.39.4\n");
        write(root, ".tool-versions", "nodejs 22.15.0\npython 3.7.2 2.7.15 system\n");
        write(root, "mise.toml", "[tools]\njava = \"temurin-21\"\nnode = \"20\"\n");
        write(root, ".java-version", "21.0.2\n");
        write(root, ".nvmrc", "v20.11.0\n");
        write(root, ".node-version", "20.10.0\n");
        write(
                root,
                ".mvn/wrapper/maven-wrapper.properties",
                "wrapperVersion=3.3.2\n"
                        + "distributionUrl=https\\://repo.maven.apache.org/maven2/org/apache/maven/"
                        + "apache-maven/3.9.9/apache-maven-3.9.9-bin.zip\n");
        write(root, ".jabbrc", "jdk: openjdk@1.11.0\n");
        write(root, ".prototools", "bun = \"1.1.0\"\n\n[plugins]\nfoo = \"bar\"\n");
        write(root, "package.json", voltaJson(0));
        write(root, "devbox.json", devboxJson(0));
        write(root, ".devcontainer/devcontainer.json", devcontainerJson(0));
        write(root, ".env", "APP_ENV=dev\nDB_PASSWORD=secret\n");
        write(root, ".envrc", "export FOO=bar\nuse nix\n");
        write(root, ".python-version", "3.12.1\n");
        write(root, ".ruby-version", "3.3.0\n");
        write(root, ".terraform-version", "1.7.0\n");
        write(root, ".bazelversion", "7.0.0\n");
        write(root, ".go-version", "1.22.0\n");
        write(root, ".mvn/devtools-extension.properties", "dotenv.allow=APP_ENV,FOO\n");
    }

    /** A {@code package.json} whose {@code volta} section comes last, after {@code filler}. */
    static String voltaJson(int filler) {
        return "{\"name\":\"x\",\"dependencies\":{"
                + fillerEntries(filler)
                + "},\"volta\":{\"node\":\"20.11.0\",\"npm\":\"10.2.4\"}}";
    }

    static String devboxJson(int filler) {
        return "{\"packages\":[\"jdk@21\",\"maven@3.9\"],\"env\":{" + fillerEntries(filler) + "}}";
    }

    static String devcontainerJson(int filler) {
        return "{\n"
                + "  // JSONC\n"
                + "  \"features\": {\n"
                + "    \"ghcr.io/devcontainers/features/java:1\": {\"version\": \"21\"},\n"
                + "    \"ghcr.io/devcontainers/features/node:1\": {\"version\": \"20\",},\n"
                + "  },\n"
                + "  \"customizations\": {"
                + fillerEntries(filler)
                + "},\n"
                + "}\n";
    }

    /** {@code count} short {@code "pkg-N":"^1.2.3"} members, roughly 20 bytes each. */
    static String fillerEntries(int count) {
        StringBuilder sb = new StringBuilder(count * 22);
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append("\"pkg-").append(i).append("\":\"^1.2.3\"");
        }
        return sb.toString();
    }
}

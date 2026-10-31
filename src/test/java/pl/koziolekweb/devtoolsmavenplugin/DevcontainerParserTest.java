package pl.koziolekweb.devtoolsmavenplugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DevcontainerParserTest {

    @TempDir Path tempDir;

    private final DevcontainerParser parser = new DevcontainerParser();

    private void write(String relative, String content) throws Exception {
        Path file = tempDir.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void parsesFeaturesFromDevcontainerDirectory() throws Exception {
        write(
                ".devcontainer/devcontainer.json",
                "{\"features\": {"
                        + "\"ghcr.io/devcontainers/features/java:1\": {\"version\": \"21\"},"
                        + "\"ghcr.io/devcontainers/features/node:1\": {\"version\": \"20\"}}}");

        Map<String, String> values = parser.parse(tempDir);

        assertEquals("21", values.get("devcontainer.java"));
        assertEquals("20", values.get("devcontainer.node"));
        assertEquals(2, values.size());
    }

    @Test
    void parsesRootLevelDevcontainerJson() throws Exception {
        write(
                ".devcontainer.json",
                "{\"features\": {"
                        + "\"ghcr.io/devcontainers/features/go:1\": {\"version\": \"1.22\"}}}");

        assertEquals("1.22", parser.parse(tempDir).get("devcontainer.go"));
    }

    @Test
    void directoryFileWinsOverRootFile() throws Exception {
        write(
                ".devcontainer/devcontainer.json",
                "{\"features\": {\"a/b/java:1\": {\"version\": \"21\"}}}");
        write(".devcontainer.json", "{\"features\": {\"a/b/java:1\": {\"version\": \"17\"}}}");

        assertEquals("21", parser.parse(tempDir).get("devcontainer.java"));
    }

    @Test
    void toleratesCommentsAndTrailingCommas() throws Exception {
        write(
                ".devcontainer/devcontainer.json",
                "// header\n"
                        + "{\n"
                        + "  /* block\n comment */\n"
                        + "  \"name\": \"x\", // trailing\n"
                        + "  \"features\": {\n"
                        + "    \"ghcr.io/devcontainers/features/java:1\": {\n"
                        + "      \"version\": \"21\", // jdk\n"
                        + "    },\n"
                        + "    \"ghcr.io/devcontainers/features/python:1\":"
                        + " {\"version\": \"3.12\",},\n"
                        + "  },\n"
                        + "  \"forwardPorts\": [8080, 9090,],\n"
                        + "}\n");

        Map<String, String> values = parser.parse(tempDir);

        assertEquals("21", values.get("devcontainer.java"));
        assertEquals("3.12", values.get("devcontainer.python"));
    }

    @Test
    void leavesStringContentIntact() throws Exception {
        write(
                ".devcontainer/devcontainer.json",
                "{\"url\": \"http://example.com/*x*/\", \"features\": {"
                        + "\"a/b/java:1\": {\"version\": \"21,}\", \"note\": \"a // b, ]\"}}}");

        assertEquals("21,}", parser.parse(tempDir).get("devcontainer.java"));
    }

    @Test
    void stringifiesNumericVersions() throws Exception {
        write(
                ".devcontainer/devcontainer.json",
                "{\"features\": {\"a/b/java:1\": {\"version\": 21},"
                        + "\"a/b/go:1\": {\"version\": 1.22},"
                        + "\"a/b/node:1\": {\"version\": 20.0}}}");

        Map<String, String> values = parser.parse(tempDir);

        assertEquals("21", values.get("devcontainer.java"));
        assertEquals("1.22", values.get("devcontainer.go"));
        assertEquals("20", values.get("devcontainer.node"));
    }

    @Test
    void skipsFeaturesWithoutVersion() throws Exception {
        write(
                ".devcontainer/devcontainer.json",
                "{\"features\": {\"a/b/git:1\": {}, \"a/b/common-utils:2\": {\"x\": \"y\"},"
                        + "\"a/b/docker:1\": \"latest\", \"a/b/java:1\": {\"version\": \"17\"}}}");

        Map<String, String> values = parser.parse(tempDir);

        assertEquals(1, values.size());
        assertEquals("17", values.get("devcontainer.java"));
    }

    @Test
    void stripsTagAndDigestFromFeatureId() throws Exception {
        write(
                ".devcontainer/devcontainer.json",
                "{\"features\": {\"localhost:5000/f/java:1.2\": {\"version\": \"21\"},"
                        + "\"ghcr.io/x/node@sha256:abc\": {\"version\": \"20\"}}}");

        Map<String, String> values = parser.parse(tempDir);

        assertEquals("21", values.get("devcontainer.java"));
        assertEquals("20", values.get("devcontainer.node"));
    }

    @Test
    void returnsEmptyMapWhenFeaturesHasWrongType() throws Exception {
        write(".devcontainer/devcontainer.json", "{\"features\": [\"java\"]}");

        assertTrue(parser.parse(tempDir).isEmpty());
    }

    @Test
    void returnsEmptyMapForInvalidJson() throws Exception {
        write(".devcontainer/devcontainer.json", "{\"features\": {\"a/b/java:1\": ");

        assertTrue(parser.parse(tempDir).isEmpty());
    }

    @Test
    void returnsEmptyMapWhenFileMissing() {
        assertTrue(parser.parse(tempDir).isEmpty());
    }

    @Test
    void keepsNumericVersionLiteralText() throws Exception {
        write(
                ".devcontainer/devcontainer.json",
                "{\"features\": {"
                        + "\"a/b/python:1\": {\"version\": 3.10},"
                        + "\"a/b/ruby:1\": {\"version\": 1.20},"
                        + "\"a/b/java:1\": {\"version\": 21},"
                        + "\"a/b/go:1\": {\"version\": 1.5}}}");

        Map<String, String> values = parser.parse(tempDir);

        assertEquals("3.10", values.get("devcontainer.python"));
        assertEquals("1.20", values.get("devcontainer.ruby"));
        assertEquals("21", values.get("devcontainer.java"));
        assertEquals("1.5", values.get("devcontainer.go"));
    }

    @Test
    void featureIdWithTagAndDigestInEitherOrder() throws Exception {
        write(
                ".devcontainer/devcontainer.json",
                "{\"features\": {"
                        + "\"ghcr.io/x/a:1@sha256:abc\": {\"version\": \"1\"},"
                        + "\"ghcr.io/x/b@sha256:abc:1\": {\"version\": \"2\"},"
                        + "\"ghcr.io/x/c\": {\"version\": \"3\"}}}");

        Map<String, String> values = parser.parse(tempDir);

        assertEquals("1", values.get("devcontainer.a"));
        assertEquals("2", values.get("devcontainer.b"));
        assertEquals("3", values.get("devcontainer.c"));
        assertEquals(3, values.size());
    }

    @Test
    void skipsFeaturesWithAnEmptyNameOrBlankVersion() throws Exception {
        write(
                ".devcontainer/devcontainer.json",
                "{\"features\": {"
                        + "\"ghcr.io/x/\": {\"version\": \"1\"},"
                        + "\":1\": {\"version\": \"2\"},"
                        + "\"ghcr.io/x/blank\": {\"version\": \"   \"},"
                        + "\"ghcr.io/x/ok\": {\"version\": \"4\"}}}");

        Map<String, String> values = parser.parse(tempDir);

        assertEquals(1, values.size());
        assertEquals("4", values.get("devcontainer.ok"));
    }
}

package pl.koziolekweb.devtoolsmavenplugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;

/**
 * Maven core extension entry point. Runs every injected {@link ToolParser} against the reactor
 * root, exposes their results as {@code sdkman.*}/{@code asdf.*} user properties (and, once
 * projects are read, as each project's own properties), and applies {@value #CONFIG_FILE}
 * transforms on top.
 */
@Named
@Singleton
public final class DevtoolsLifecycleParticipant extends AbstractMavenLifecycleParticipant {

    static final String CONFIG_FILE = ".mvn/devtools-extension.properties";

    private final List<ToolParser> parsers;
    private final List<String> namespaces;
    private Set<String> managedPropertyNames = Collections.emptySet();

    /**
     * Creates the participant with every {@link ToolParser} Sisu could find.
     *
     * @param parsers every {@link ToolParser} Sisu can find (one per supported version-pin file),
     *     injected by the container.
     */
    @Inject
    public DevtoolsLifecycleParticipant(List<ToolParser> parsers) {
        this.parsers = parsers;
        this.namespaces = namespacesOf(parsers);
    }

    /**
     * The property prefixes of {@code parsers}, sorted alphabetically and without duplicates. This
     * is the order in which a bare transform source is resolved, independent of the order Sisu
     * injects the parsers in.
     */
    static List<String> namespacesOf(List<ToolParser> parsers) {
        Set<String> sorted = new TreeSet<>();
        for (ToolParser parser : parsers) {
            sorted.add(parser.propertyPrefix());
        }
        return Collections.unmodifiableList(new ArrayList<>(sorted));
    }

    static Path determineRoot(MavenExecutionRequest request) {
        File multiModuleRoot = request.getMultiModuleProjectDirectory();
        if (multiModuleRoot != null) {
            return multiModuleRoot.toPath().toAbsolutePath().normalize();
        }
        String baseDirectory = request.getBaseDirectory();
        if (baseDirectory != null && !baseDirectory.trim().isEmpty()) {
            return new File(baseDirectory).toPath().toAbsolutePath().normalize();
        }
        return new File(".").toPath().toAbsolutePath().normalize();
    }

    @Override
    public void afterSessionStart(MavenSession session) throws MavenExecutionException {
        MavenExecutionRequest request = session.getRequest();
        Path root = determineRoot(request);

        try {
            Properties userProperties = request.getUserProperties();
            Set<String> managedKeys = new LinkedHashSet<>();

            // -D... wins over any version-pin file. Each parser owns its own file name and
            // property prefix and returns already-namespaced keys (or an empty map when its
            // file is absent).
            for (ToolParser parser : parsers) {
                for (Map.Entry<String, String> entry : parser.parse(root).entrySet()) {
                    managedKeys.add(entry.getKey());
                    userProperties.putIfAbsent(entry.getKey(), entry.getValue());
                }
            }

            Path configPath = root.resolve(CONFIG_FILE);
            if (Files.isRegularFile(configPath)) {
                TransformConfiguration configuration =
                        TransformConfiguration.load(configPath, namespaces);
                managedKeys.addAll(configuration.apply(userProperties));
            }

            managedPropertyNames = managedKeys;
        } catch (IOException | IllegalArgumentException e) {
            throw new MavenExecutionException(
                    "Cannot load dev tool version properties from " + root + ": " + e.getMessage(),
                    e);
        }
    }

    @Override
    public void afterProjectsRead(MavenSession session) {
        Properties userProperties = session.getRequest().getUserProperties();

        // Mirror the properties this participant resolved into each project's own
        // properties so
        // they show up in effective-pom and can be read via project.getProperties() by
        // other
        // plugins, not just interpolated where explicitly referenced. Explicit POM
        // declarations
        // still win.
        for (String name : managedPropertyNames) {
            String value = userProperties.getProperty(name);
            if (value == null) {
                continue;
            }
            for (MavenProject project : session.getProjects()) {
                project.getProperties().putIfAbsent(name, value);
            }
        }
    }
}

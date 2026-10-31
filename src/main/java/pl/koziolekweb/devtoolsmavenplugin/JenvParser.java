package pl.koziolekweb.devtoolsmavenplugin;

import javax.inject.Named;
import javax.inject.Singleton;

/** Reads {@code .java-version} (a single bare version) into {@code jenv.java}. */
@Named
@Singleton
final class JenvParser extends SingleValueToolParser {

    static final String FILE_NAME = ".java-version";
    static final String PREFIX = "jenv.";
    static final String KEY = "java";

    JenvParser() {
        super(FILE_NAME, PREFIX, KEY);
    }
}

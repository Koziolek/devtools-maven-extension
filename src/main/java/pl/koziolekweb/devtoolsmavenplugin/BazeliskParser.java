package pl.koziolekweb.devtoolsmavenplugin;

import javax.inject.Named;
import javax.inject.Singleton;

/** Reads {@code .bazelversion} (a single bare version) into {@code bazelisk.bazel}. */
@Named
@Singleton
final class BazeliskParser extends SingleValueToolParser {

    static final String FILE_NAME = ".bazelversion";
    static final String PREFIX = "bazelisk.";
    static final String KEY = "bazel";

    BazeliskParser() {
        super(FILE_NAME, PREFIX, KEY);
    }
}

package pl.koziolekweb.devtoolsmavenplugin;

import javax.inject.Named;
import javax.inject.Singleton;

/** Reads {@code .ruby-version} (a single bare version) into {@code rbenv.ruby}. */
@Named
@Singleton
final class RbenvParser extends SingleValueToolParser {

    static final String FILE_NAME = ".ruby-version";
    static final String PREFIX = "rbenv.";
    static final String KEY = "ruby";

    RbenvParser() {
        super(FILE_NAME, PREFIX, KEY);
    }
}

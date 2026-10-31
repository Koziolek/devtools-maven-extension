package pl.koziolekweb.devtoolsmavenplugin;

import javax.inject.Named;
import javax.inject.Singleton;

/** Reads {@code .go-version} (a single bare version) into {@code goenv.go}. */
@Named
@Singleton
final class GoenvParser extends SingleValueToolParser {

    static final String FILE_NAME = ".go-version";
    static final String PREFIX = "goenv.";
    static final String KEY = "go";

    GoenvParser() {
        super(FILE_NAME, PREFIX, KEY);
    }
}

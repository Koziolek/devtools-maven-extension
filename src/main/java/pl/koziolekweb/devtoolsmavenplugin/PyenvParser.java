package pl.koziolekweb.devtoolsmavenplugin;

import javax.inject.Named;
import javax.inject.Singleton;

/** Reads {@code .python-version} (a single bare version) into {@code pyenv.python}. */
@Named
@Singleton
final class PyenvParser extends SingleValueToolParser {

    static final String FILE_NAME = ".python-version";
    static final String PREFIX = "pyenv.";
    static final String KEY = "python";

    PyenvParser() {
        super(FILE_NAME, PREFIX, KEY);
    }
}

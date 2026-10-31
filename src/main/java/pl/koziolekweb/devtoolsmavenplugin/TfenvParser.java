package pl.koziolekweb.devtoolsmavenplugin;

import javax.inject.Named;
import javax.inject.Singleton;

/** Reads {@code .terraform-version} (a single bare version) into {@code tfenv.terraform}. */
@Named
@Singleton
final class TfenvParser extends SingleValueToolParser {

    static final String FILE_NAME = ".terraform-version";
    static final String PREFIX = "tfenv.";
    static final String KEY = "terraform";

    TfenvParser() {
        super(FILE_NAME, PREFIX, KEY);
    }
}

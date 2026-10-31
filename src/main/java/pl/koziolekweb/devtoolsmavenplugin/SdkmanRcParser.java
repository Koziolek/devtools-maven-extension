package pl.koziolekweb.devtoolsmavenplugin;

import javax.inject.Named;
import javax.inject.Singleton;

@Named
@Singleton
final class SdkmanRcParser extends AbstractKeyValueLineParser {

    static final String FILE_NAME = ".sdkmanrc";
    static final String PREFIX = "sdkman.";
    static final char SEPARATOR = '=';

    SdkmanRcParser() {
        super(FILE_NAME, PREFIX, SEPARATOR);
    }
}

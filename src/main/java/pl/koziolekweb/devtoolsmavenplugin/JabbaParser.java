package pl.koziolekweb.devtoolsmavenplugin;

import javax.inject.Named;
import javax.inject.Singleton;

@Named
@Singleton
final class JabbaParser extends AbstractKeyValueLineParser {

    static final String FILE_NAME = ".jabbrc";
    static final String PREFIX = "jabba.";
    static final char SEPARATOR = ':';

    JabbaParser() {
        super(FILE_NAME, PREFIX, SEPARATOR);
    }
}

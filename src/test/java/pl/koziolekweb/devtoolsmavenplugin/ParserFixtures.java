package pl.koziolekweb.devtoolsmavenplugin;

import java.util.stream.Stream;

class ParserFixtures {
    static Object[][] asdfSingleValueExpected() {
        return new Object[][] {
            {"nodejs \npython 3.12.3\n", "asdf.python", "3.12.3"}, // rejectsToolNameWithNoVersion
            {"nodejs 18.20.4\nnodejs 22.15.0\n", "asdf.nodejs", "18.20.4"}, // firstDuplicateKeyWins
            {"nodejs # LTS\npython 3.12.3\n", "asdf.python", "3.12.3"}, // firstDuplicateKeyWins
            {
                "python 3.7.2 2.7.15 system\n", "asdf.python", "3.7.2 2.7.15 system"
            }, // keepsEverythingAfterFirstSeparator
        };
    }

    static Stream<String> voltaExpectedEmpty() {
        return Stream.of(
                "{\"name\":\"x\",\"version\":\"1.0.0\"}", // returnsEmptyMapWithoutVoltaSection
                "{\"volta\":\"20.11.0\"}", // returnsEmptyMapWhenVoltaIsNotAnObject
                "{\"volta\": {\"node\": " // returnsEmptyMapForInvalidJson
                );
    }
}

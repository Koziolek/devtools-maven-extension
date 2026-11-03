# Dev Tools Maven Core Extension

Maven Core Extension that reads `.sdkmanrc`, `.tool-versions` and other dev-tool version files (see
[Files searched](#files-searched)) from the reactor root and
injects each entry into Maven's **user properties before project construction**.

Given `.sdkmanrc`:

```properties
java=25-open
quarkus=3.39.4
```

it exposes:

```text
sdkman.java=25-open
sdkman.quarkus=3.39.4
```

Given `.tool-versions` (asdf, [format reference](https://asdf-vm.com/manage/configuration.html)):

```text
nodejs 22.15.0
python 3.7.2 2.7.15 system
```

it exposes:

```text
asdf.nodejs=22.15.0
asdf.python=3.7.2 2.7.15 system
```

The property value is everything after the first space, verbatim — including any fallback
versions or a vendor marker like `system`. Use a [transformation](#transformations) to pick out
just the primary version, a fallback, or the vendor.

`.tool-versions` follows asdf's own parsing rules: the tool and its versions are separated by any run
of whitespace (spaces and/or tabs), and everything after the first `#` is a comment
(`nodejs 20.1.0 # LTS` gives `20.1.0`). If a tool is listed more than once, the first entry wins,
as in asdf. A line with a tool but no version (including `nodejs # LTS`) is logged at `WARN` and
skipped. `.sdkmanrc` is unchanged: `key=value`, no inline comments, the last duplicate wins (as in
sdkman-cli).

Both files are read independently — a project can use either or both. Those values are
available during model interpolation, so a POM can use:

```xml
<properties>
  <quarkus.version>${sdkman.quarkus}</quarkus.version>
  <maven.compiler.release>${sdkman.java.version}</maven.compiler.release>
  <node.version>${asdf.nodejs}</node.version>
</properties>
```

## Malformed lines

A line is split on the first separator (`=` for `.sdkmanrc`, whitespace for `.tool-versions`);
both the key and the value must be non-empty. A line with no separator, an empty key, or an
empty value (e.g. `java=`) is not a valid entry — it is logged at `WARN` and skipped, the rest
of the file is still processed, and the build does not fail.

This matches sdkman-cli's own validation, which requires a value after `=`
(`^[[:lower:]]+\=.+$` in
[`sdkman-env.sh`](https://github.com/sdkman/sdkman-cli/blob/master/src/main/bash/sdkman-env.sh));
`java=` is rejected there too.

## Unreadable files

A file that exists but cannot be read (for example it is not valid UTF-8, or the process has no
permission) is treated like a missing file for that tool: it is logged at `ERROR` with its full
path, contributes no properties, and the build does not fail. The other tools are still
processed. (The same holds for a file with invalid JSON or TOML: `ERROR` with the path, the
whole file is ignored.) Only a broken `.mvn/devtools-extension.properties` (a developer error,
see Transformations) fails the build.

## Transformations

Optional transformations are configured in `.mvn/devtools-extension.properties`.

Example:

```properties
transform.java.version.source=java
transform.java.version.pattern=^([0-9]+).*$
transform.java.version.replacement=$1
```

For `java=25-open` (from `.sdkmanrc`), this creates:

```text
sdkman.java.version=25
```

Each transformation has the form:

```properties
transform.<target>.source=<key, or <namespace>.<key>>
transform.<target>.pattern=<Java regular expression>
transform.<target>.replacement=<Matcher replacement>
```

Transformations are applied in dependency order: if the `source` of one transformation is the
target of another, the latter runs first (ties are broken alphabetically, so the order is
deterministic). Dependencies are matched by name, ignoring the namespace, so two unrelated
transformations can in rare cases look like a cycle. A circular dependency fails the build with a
descriptive error.

The `replacement` is applied with `Matcher#replaceFirst`: only the first match of `pattern` is
replaced and the rest of the source value is kept (use `^...$` or `.*` in the pattern to transform
the whole value). `$1`, `$2`, ... refer to capture groups; a literal `$` or `\` must be escaped as
`\$` / `\\`. An invalid replacement (for example `$2` when the pattern has only one group, a lone
`$`, or a trailing `\`) is a configuration error and fails the build with a message naming the
transformation, the replacement and the pattern.

`source` may be a bare key or an explicit `<namespace>.<key>` reference (e.g. `sdkman.java`,
`nvm.node`, `mise.java`). A bare key is resolved against the namespaces of all
supported tools in **alphabetical order**, first hit wins: `asdf.`, `bazelisk.`, `devbox.`,
`devcontainer.`, `dotenv.`, `goenv.`, `jabba.`, `jenv.`, `mise.`, `mvnw.`, `nvm.`, `proto.`,
`pyenv.`, `rbenv.`, `sdkman.`, `tfenv.`, `volta.`. So `source=java` with both `asdf.java` and
`sdkman.java` present reads `asdf.java`: use the explicit form (`source=sdkman.java`) whenever the
same key can come from several tools. A bare key found nowhere is reported as missing in any
namespace. The generated property is
`<namespace>.<target>`, matching whichever namespace the source resolved to.

Since an `asdf.<key>` value can be a whole fallback list (e.g. `3.7.2 2.7.15 system` for
`python`), a pattern can pick out any piece of it. For example, to split the entry above into a
primary version and a vendor marker:

```properties
transform.python.version.source=asdf.python
transform.python.version.pattern=^(\\S+).*$
transform.python.version.replacement=$1

transform.python.vendor.source=asdf.python
transform.python.vendor.pattern=.*\\s(\\S+)$
transform.python.vendor.replacement=$1
```

(`\\S`/`\\s`, not `\S`/`\s` — this file is parsed with `java.util.Properties`, which unescapes a
single backslash away.)

which creates:

```text
asdf.python.version=3.7.2
asdf.python.vendor=system
```

## Precedence

Properties explicitly supplied with `-D` win. For example:

```bash
mvn -Dsdkman.java=21-temurin -Dsdkman.java.version=21 verify
```

will not be overwritten by `.sdkmanrc`, `.tool-versions`, or a transformation.

## Installation

Build and install the extension:

```bash
mvn install
```

Then register it in the target project's `.mvn/extensions.xml`:

```xml
<extensions xmlns="http://maven.apache.org/EXTENSIONS/1.0.0">
  <extension>
    <groupId>pl.koziolekweb</groupId>
    <artifactId>devtools-maven-extension</artifactId>
    <version>1.0.0</version>
  </extension>
</extensions>
```

Maven loads `.mvn/extensions.xml` as a core extension before constructing projects.

## Files searched

All paths are resolved against the reactor root
(`MavenExecutionRequest#getMultiModuleProjectDirectory()`).

| File | Tool | Property |
|---|---|---|
| `.sdkmanrc` | SDKMAN! | `sdkman.<key>` |
| `.tool-versions` | asdf | `asdf.<key>` |
| `mise.toml`, `.mise.toml` | mise (`[tools]` table only, real TOML; `mise.toml` wins) | `mise.<tool>` |
| `.java-version` | jenv | `jenv.java` |
| `.nvmrc`, `.node-version` | nvm / fnm / nodenv (`.nvmrc` wins) | `nvm.node` |
| `.mvn/wrapper/maven-wrapper.properties` | Maven Wrapper | `mvnw.<property>`, plus `mvnw.maven` (version from `distributionUrl`) |
| `.jabbrc` | Jabba | `jabba.<key>` |
| `.prototools` | proto (top-level keys only, real TOML) | `proto.<tool>` |
| `package.json` | Volta (`volta` object, string values) | `volta.<tool>` |
| `devbox.json` | Devbox (`packages`, `name@version`, JSONC) | `devbox.<package>` |
| `.devcontainer/devcontainer.json`, `.devcontainer.json` | Dev Containers (`features.*.version`, JSONC) | `devcontainer.<feature>` |
| `.env`, `.envrc` | dotenv / direnv (`KEY=value`; `.env` wins; only keys listed in `dotenv.allow`) | `dotenv.<KEY>` (not `env.`, which is Maven's own) |
| `.python-version` | pyenv | `pyenv.python` |
| `.ruby-version` | rbenv / rvm | `rbenv.ruby` |
| `.terraform-version` | tfenv | `tfenv.terraform` |
| `.bazelversion` | Bazelisk | `bazelisk.bazel` |
| `.go-version` | goenv | `goenv.go` |
| `.mvn/devtools-extension.properties` | transformations | see above |

`mise.toml` and `.prototools` are parsed as real TOML with the
[toml-java](https://github.com/vieiro/toml-java) library, so multi-line arrays, quoted and dotted
keys, `[tools.node]` tables and comments all work as the TOML spec says. In `[tools]` a value is a
string, a number, an array (the first element wins: `node = [ "20", "18" ]` gives `mise.node=20`) or
a table with a `version`; any other value (an empty array, a boolean, a table without `version`) is
logged at `WARN` per tool and skipped. In `.prototools` only top-level strings and numbers count;
tables are ignored.

TOML is validated as a whole, unlike the line-oriented formats: a syntax error anywhere in the
file, or a duplicate key, makes the library reject the document, so the **whole file** is ignored
with one `ERROR` ("Invalid TOML in <path>:" followed by the line and column of each error); the
other tools, and `.mise.toml` when `mise.toml` is the broken one, are still read. Quote versions that must keep a trailing zero:
`python = 3.10` is the float `3.1` in TOML, `python = "3.10"` stays `3.10`.

JSON files are read as JSONC: `//` and `/* */` comments and trailing commas are tolerated in
`devbox.json` (as Devbox itself does), `devcontainer.json` and also `package.json`. A duplicate
key in an object makes the file invalid. If both `.devcontainer/devcontainer.json` and
`.devcontainer.json` exist, both are read and the features are merged; for the same feature the
one in `.devcontainer/` wins. Numeric `version` values in `devcontainer.json` keep their literal text
(`3.10` stays `3.10`; integral numbers are rendered without a fraction, `20.0` becomes `20`).

### `.env` files and secrets

`.env` and `.envrc` usually hold secrets, and every exported key becomes a Maven user property that
shows up in `mvn -X`, `help:effective-pom`, CI logs and forked JVMs. So **nothing is exported
by default**: list the keys you want in `.mvn/devtools-extension.properties`:

```properties
dotenv.allow=APP_ENV,JAVA_OPTS_EXTRA
```

The list is separated by commas and/or whitespace; names are case sensitive and match the key in
the file (`APP_ENV` gives `dotenv.APP_ENV`). `dotenv.allow=*` exports everything, which is only
sensible when the files are known to hold no secrets. If a file has `KEY=value` entries but none is
allowed, one `INFO` line names the ignored keys (never their values) and points to this setting.

Single-value files (`.java-version`, `.nvmrc`, ...) use the first line that is not blank or a `#`
comment, verbatim (so `.nvmrc` containing `v20.11.0` yields `v20.11.0`).

### Several tools in one project

Every tool has its own namespace, so nothing is overwritten when a project uses more than one.
In particular, mise also reads `.tool-versions`: if both `.tool-versions` and `mise.toml` exist you
get both `asdf.java` and `mise.java`. `.nvmrc` and `.node-version` share `nvm.node` and are handled
by a single parser with a fixed precedence: `.nvmrc` wins and `.node-version` is only a fallback
(used when `.nvmrc` is absent or has no value). If both exist and differ, a `WARN` naming both
files and values is logged. The value is kept verbatim (a leading `v` stays; strip it with a
transform). Values from `-D` on the Maven command line
always win over everything (see Precedence).

If none of these files exists, the extension is a no-op.

## Code style

Formatting is checked in two places, and **checkstyle wins** where they disagree:

- **Spotless** with google-java-format (AOSP style) formats the code and fixes the import order. It
  runs at the `validate` phase, before checkstyle, so `mvn install` repairs formatting by itself;
  `mvn spotless:apply` does it on demand.
- **checkstyle** (`checkstyle.xml`) runs right after it and reports at `WARN`; it does not fail the
  build, but the project is kept free of warnings.

google-java-format indents `case` labels by 4, `checkstyle.xml` asks for 2 and the formatter cannot
be configured. A `switch` is therefore indented by hand and wrapped in `// spotless:off` /
`// spotless:on` (`<toggleOffOn/>` in the Spotless configuration); there is one such block in the
sources. Naming rules are strict too: no two consecutive capital letters in a name, so
`InASingleModule` or `stripsLeadingVPrefix` are rejected, and a method name has a lowercase second
letter.

## Mutation testing

[PIT](https://pitest.org) (pitest) mutates the bytecode and checks that the tests notice. It runs in `mvn verify`
(execution `pitest-verify`), takes about a minute and is **report only**: it has no thresholds, so a low score never
fails the build.

- Report: `target/pit-reports/index.html` (`mutations.xml` next to it).
- Alone, without the rest of the lifecycle: `mvn test-compile pitest:mutationCoverage`.
- Skip: `-DskipPitest=true`.
- Baseline (2026-11-03): 291 mutants, 268 killed (92 %), line coverage of the mutated classes 99 %.
- Tests run on one thread (`LogCapture` swaps `System.err`). The POSIX permission tests are skipped as root, so
  the "unreadable file" paths show up as surviving mutants there.

## Benchmarks

JMH benchmarks live in `src/benchmark/java` and are only built with the `benchmarks` profile, so
a normal `mvn install` neither compiles nor runs them and JMH never ends up in the artifact.

They are started by a JUnit test, `BenchmarkRunnerTest` (tag `benchmark`), which calls the JMH
`Runner` API. With the profile active it is an ordinary test, so no extra options are needed:

```bash
mvn -Pbenchmarks test                                                        # tests + all benchmarks
mvn -Pbenchmarks test -Dtest=BenchmarkRunnerTest                             # benchmarks only
mvn -Pbenchmarks test -Dtest=BenchmarkRunnerTest -Djmh.include=ColdStart     # one benchmark (regex)
```

In the IDE you can run `BenchmarkRunnerTest` directly (set `-Djmh.include=...` to narrow it).
Results are written to `target/jmh-result.json` (override with `-Djmh.result=...`). The JaCoCo
agent is not passed on to the JMH forks, because it would inflate the cold start numbers.

The profile is activated automatically while IntelliJ imports the project (it sets `idea.version`),
which makes `src/benchmark/java` a test source root in the IDE. In other IDEs enable `benchmarks`
by hand. On JDK 23+ the JMH generator is configured through `annotationProcessorPaths`, because
`javac` no longer runs processors found only on the classpath.

A full run takes about ten minutes; `ColdStartBenchmark` is the one closest to what a Maven build
actually pays, because it measures the first `parse` call in a fresh JVM.

### Performance

Baseline from a full run (JMH 1.37, JDK 25.0.5, no JaCoCo agent in the forks) with the current 17
parsers, after strict UTF-8 reading in `JsonFiles` and comment stripping for every JSON file.
Two full runs on the same machine differed by 5–7 %. Absolute numbers depend on the machine; compare against a run on the same one. The overhead measured here was judged
acceptable for this project, so the numbers are a reference, not a target to squeeze.

**What a build pays.** A Maven build starts a fresh JVM, so the first call matters more than the
steady state (`ColdStartBenchmark`, one `parse` call per fork, mean of 10 forks):

| Situation                                                  | Time      |
|------------------------------------------------------------|-----------|
| No tool files in the project (all parsers)                 | ≈ 5.0 ms  |
| All tool files present (all parsers)                       | ≈ 57 ms   |
| `MiseParser` / `ProtoParser` alone                         | 41.3 / 41.7 ms |
| `VoltaParser` / `DevboxParser` / `DevcontainerParser` alone | 7.7 / 7.2 / 7.7 ms |
| `SdkmanRcParser` alone                                     | ≈ 0.8 ms  |

The cost is class loading, not parsing. The TOML parsers load toml-java and its ANTLR runtime
(≈ 41 ms, the biggest cost in the extension, shared by `MiseParser` and `ProtoParser`, so both
together cost about as much as one). The JSON parsers load Moshi, okio and the Kotlin runtime
(≈ 7–8 ms, also shared). A parser whose file is absent returns before touching its library: with no
`mise.toml`, `.prototools`, `package.json`, `devbox.json` or `devcontainer.json` those parsers cost
0.3–1 ms each. Everything else is below the
noise of a Maven start. So a project pays for TOML only if it has a `mise.toml` or `.prototools`,
and for JSON only if it has one of the JSON files.

**Steady state** (warm JIT, one call):

| Operation                                          | Time                   |
|----------------------------------------------------|------------------------|
| One parser, file missing                           | ≈ 1 µs (2 µs for parsers that check two files: Mise, Nvm, Devcontainer, Dotenv) |
| One parser, typical file                           | 8–11 µs                |
| `MiseParser` / `ProtoParser`                       | 17.0 / 15.3 µs         |
| `MavenWrapperParser` / `DotenvParser` / `NvmParser` | 12.6 / 34.5 / 34.1 µs |
| All parsers, all files present                     | 242 µs                 |
| All parsers, empty directory                       | 23 µs                  |
| `afterSessionStart`, all files + 2 transforms      | 263 µs                 |
| `afterSessionStart`, empty directory               | 24 µs                  |

**Scaling with file size** (`FileSizeBenchmark`; lines of real content, mean time):

| Parser            | 10 lines | 1 000 lines | 100 000 lines |
|-------------------|----------|-------------|---------------|
| `SdkmanRcParser`  | 9.6 µs   | 133 µs      | 18.8 ms       |
| `AsdfToolVersionsParser` | 9.6 µs | 164 µs | 22.3 ms        |
| `MiseParser`      | 24.6 µs  | 1.34 ms     | 150 ms        |
| `ProtoParser`     | 22.3 µs  | 1.22 ms     | 144 ms        |
| `DotenvParser`    | 19.6 µs  | 334 µs      | 38.1 ms       |
| `NvmParser` (needs only line 1) | 10.4 µs | 35 µs | 2.8 ms    |

Cost grows linearly with the number of lines. The TOML parsers are about 8 times slower per line
than the line-oriented ones, because the whole document is built into a tree. `NvmParser` shows the
price of reading the whole file through `WithLineReader` even when the first line is enough:
≈ 28 ns per line. Real version pin files have a handful of lines, so none of this is visible in
practice.

**JSON** (`JsonParserBenchmark`): ≈ 7.6–10.5 µs for a typical small file; a synthetic ≈ 1 MB document
takes 12.8 ms (Volta), 12.3 ms (Devbox) and 12.9 ms (Devcontainer), because the whole document is
parsed to read one section.

**Memory** (`MemoryFootprintBenchmark`, 10 kB to 10 MB, bytes allocated per `parse` call as
`gc.alloc.rate.norm` from JMH's `GCProfiler`; this is allocation including garbage, not retained
heap). Allocation is linear in the file size for every parser: a log-log slope of 0.92–1.00 over
three orders of magnitude, and the bytes per byte of file stay flat (they fall a little with size,
as the fixed cost is amortised).

| Parsers                                                           | Bytes allocated per byte of file (10 kB / 10 MB) | 10 MB file |
|-------------------------------------------------------------------|--------------------------------------------------|------------|
| Single-version files (`Nvm`, `Jenv`, `Pyenv`, `Rbenv`, `Tfenv`, `Bazelisk`, `Goenv`) | 7.6 / 4.3 | 44 MB  |
| `SdkmanRcParser` / `JabbaParser` / `MavenWrapperParser`           | 23–28 / 17–19                                    | 170–195 MB |
| `AsdfToolVersionsParser`                                          | 29.7 / 22.3                                      | 228 MB     |
| `DotenvParser`                                                    | 41.5 / 29.6                                      | 303 MB     |
| `VoltaParser` / `DevboxParser` / `DevcontainerParser`             | 14–15 / 14.4                                     | 148 MB     |
| `MiseParser` / `ProtoParser`                                      | 99–110 / 75                                      | 763–776 MB |

The TOML figures at 10–100 kB vary by about 20 % between runs (JIT warm-up); from 1 MB up they
vary by 2 % or less, which is where the linearity is measured.

The floor is `WithLineReader` reading the whole file (≈ 4–8 bytes per byte: the line list and its
strings), which even the single-version parsers pay. The key/value parsers add the result map, and
the TOML parsers about 75–100 bytes per byte because toml-java builds a full tree; a 10 MB
`mise.toml` allocates ≈ 0.8 GB. Real files are a few hundred bytes, so this only matters for
absurd inputs.

**Transforms** (`TransformBenchmark`): `apply` costs ≈ 1.1 µs for 1 transform, 73 µs for 50 and
1.0 ms for 500 (≈ 2 µs each, the regex is compiled on every `apply`). Loading the file adds ≈ 6 µs plus ≈ 1 µs per transform.

**Conclusion.** The steady-state cost is microseconds and no parser is pathologically slow. The
measurable cost is one-off class loading: ≈ 41 ms for toml-java in projects with a `mise.toml` or
`.prototools`, ≈ 7 ms for Moshi in projects with a JSON tool file. Projects with neither pay
≈ 5.0 ms for all parsers together.

## License

[MIT](LICENSE).

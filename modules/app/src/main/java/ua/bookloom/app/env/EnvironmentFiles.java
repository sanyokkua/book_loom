package ua.bookloom.app.env;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

/**
 * Linux: the user's {@code environment.d/*.conf} files (a later file wins, as systemd reads them), then
 * {@code /etc/environment}, read as {@code KEY=VALUE} lines each time a variable is asked for.
 */
@Slf4j
final class EnvironmentFiles implements OsEnvironment {

    private static final String EXPORT = "export ";

    private final Path userDirectory;
    private final Path systemFile;

    EnvironmentFiles(final Path userDirectory, final Path systemFile) {
        this.userDirectory = Objects.requireNonNull(userDirectory, "userDirectory");
        this.systemFile = Objects.requireNonNull(systemFile, "systemFile");
    }

    @Override
    public Optional<String> read(final String name) {
        Objects.requireNonNull(name, "name");
        // Every source is read and the last assignment kept, so a later file overrides an earlier one.
        final Optional<String> user = last(userFiles().stream().map(file -> valueIn(file, name)));
        final Optional<String> found =
                user.isPresent() || !Files.isRegularFile(systemFile) ? user : valueIn(systemFile, name);
        log.debug("environment files name={} directory={} found={}", name, userDirectory, found.isPresent());
        return found.filter(value -> !value.isEmpty());
    }

    @Override
    public String source() {
        return "environment-files";
    }

    private List<Path> userFiles() {
        if (!Files.isDirectory(userDirectory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(userDirectory)) {
            return files.filter(file -> file.getFileName().toString().endsWith(".conf"))
                    .filter(Files::isRegularFile)
                    .sorted()
                    .toList();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static Optional<String> valueIn(final Path file, final String name) {
        try {
            return last(Files.readAllLines(file, StandardCharsets.UTF_8).stream()
                    .map(line -> assignment(line.strip(), name)));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static Optional<String> last(final Stream<Optional<String>> values) {
        return values.flatMap(Optional::stream).reduce((earlier, later) -> later);
    }

    private static Optional<String> assignment(final String line, final String name) {
        final String body =
                line.startsWith(EXPORT) ? line.substring(EXPORT.length()).strip() : line;
        final int equals = body.indexOf('=');
        if (body.startsWith("#")
                || equals <= 0
                || !body.substring(0, equals).strip().equals(name)) {
            return Optional.empty();
        }
        return Optional.of(unquoted(body.substring(equals + 1).strip()));
    }

    private static String unquoted(final String value) {
        final boolean quoted = value.length() >= 2
                && (value.charAt(0) == '"' || value.charAt(0) == '\'')
                && value.charAt(value.length() - 1) == value.charAt(0);
        return quoted ? value.substring(1, value.length() - 1) : value;
    }
}

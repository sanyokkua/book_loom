package ua.bookloom.app.env;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * macOS: the variables {@code launchctl setenv} gave the user's launchd, which a Dock or Finder start inherits only
 * when they were set before it; asking {@code launchctl getenv} sees one set after BookLoom started.
 */
@Slf4j
final class LaunchctlEnvironment implements OsEnvironment {

    private final CommandRunner runner;

    LaunchctlEnvironment(final CommandRunner runner) {
        this.runner = Objects.requireNonNull(runner, "runner");
    }

    @Override
    public Optional<String> read(final String name) {
        Objects.requireNonNull(name, "name");
        final CommandRunner.Output output = runner.run(List.of("launchctl", "getenv", name));
        // launchctl prints the value and a line end, or nothing at all when the variable is unset.
        final String value = output.exitCode() == 0 ? output.stdout().replaceFirst("\\R\\z", "") : "";
        log.debug("launchctl getenv name={} exit={} found={}", name, output.exitCode(), !value.isEmpty());
        return value.isEmpty() ? Optional.empty() : Optional.of(value);
    }

    @Override
    public String source() {
        return "launchctl";
    }
}

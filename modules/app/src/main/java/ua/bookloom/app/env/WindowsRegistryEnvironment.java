package ua.bookloom.app.env;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/**
 * Windows: the user's environment ({@code setx NAME value}), then the machine's, as the registry holds them now — a
 * running process keeps the environment it started with, so one set later is seen only here.
 */
@Slf4j
final class WindowsRegistryEnvironment implements OsEnvironment {

    private static final List<String> HIVES =
            List.of("HKCU\\Environment", "HKLM\\SYSTEM\\CurrentControlSet\\Control\\Session Manager\\Environment");
    // `    NAME    REG_SZ    value`; an empty value prints no third column at all.
    private static final Pattern VALUE_LINE = Pattern.compile("^\\s+(\\S+)\\s+REG_\\w+(?: {4}(.*))?$");

    private final CommandRunner runner;

    WindowsRegistryEnvironment(final CommandRunner runner) {
        this.runner = Objects.requireNonNull(runner, "runner");
    }

    @Override
    public Optional<String> read(final String name) {
        Objects.requireNonNull(name, "name");
        for (final String hive : HIVES) {
            final CommandRunner.Output output = runner.run(List.of("reg", "query", hive, "/v", name));
            // reg exits with 1 when the value does not exist.
            final Optional<String> value = output.exitCode() == 0 ? valueOf(output.stdout(), name) : Optional.empty();
            log.debug("reg query hive={} name={} exit={} found={}", hive, name, output.exitCode(), value.isPresent());
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }

    private static Optional<String> valueOf(final String stdout, final String name) {
        return stdout.lines()
                .map(VALUE_LINE::matcher)
                .filter(Matcher::matches)
                .filter(line -> line.group(1).equalsIgnoreCase(name))
                .map(line -> line.group(2) == null ? "" : line.group(2).strip())
                .filter(value -> !value.isEmpty())
                .findFirst();
    }

    @Override
    public String source() {
        return "windows-registry";
    }
}

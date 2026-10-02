package ua.bookloom.app;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The operating system's own command that keeps the computer awake for as long as it runs, tied to this process so it
 * ends with the application however the application ends. Local only: nothing here touches the network.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class KeepAwakeCommand {

    /**
     * The command for an operating system.
     *
     * @param osName the {@code os.name} value
     * @param pid this process's id, which the command watches and exits with
     * @param onPath tells whether a program is on the {@code PATH}
     * @return {@code caffeinate} on macOS (idle and disk sleep), {@code systemd-inhibit} on Linux where it is
     *     installed; empty on Windows and on a Linux without it
     */
    static Optional<List<String>> forOs(final String osName, final long pid, final Predicate<String> onPath) {
        Objects.requireNonNull(osName, "osName");
        Objects.requireNonNull(onPath, "onPath");
        final String os = osName.toLowerCase(Locale.ROOT);
        if (os.startsWith("mac") || os.startsWith("darwin")) {
            return Optional.of(List.of("caffeinate", "-i", "-m", "-w", String.valueOf(pid)));
        }
        if (os.startsWith("windows") || !onPath.test("systemd-inhibit")) {
            return Optional.empty();
        }
        return Optional.of(List.of(
                "systemd-inhibit",
                "--what=idle:sleep",
                "--who=BookLoom",
                "--why=A translation is running",
                "--mode=block",
                "tail",
                "--pid=" + pid,
                "-f",
                "/dev/null"));
    }
}

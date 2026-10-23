package ua.bookloom.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Works out the command that starts BookLoom again the way this copy was started. A plain {@code java} with no
 * recorded arguments is not enough to start the application (the class or jar is unknown), so it is refused rather
 * than guessed; a packaged launcher needs no arguments.
 */
// Checkstyle parses source before Lombok runs, so it cannot see the private constructor generated below (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class RestartCommand {

    private static final Set<String> BARE_JAVA = Set.of("java", "javaw");
    private static final String EXE = ".exe";

    /**
     * Builds the command from what the running process reports about itself.
     *
     * @param info the current process's description
     * @return the command followed by its arguments, kept one element each; empty when there is no command or it is a
     *     bare {@code java} whose arguments are unknown
     */
    static Optional<List<String>> of(final ProcessHandle.Info info) {
        final Optional<String> command = info.command().filter(text -> !text.isBlank());
        if (command.isEmpty()) {
            return Optional.empty();
        }
        final Optional<String[]> arguments = info.arguments();
        if (arguments.map(values -> values.length == 0).orElse(true) && isBareJava(command.get())) {
            return Optional.empty();
        }
        final List<String> line = new ArrayList<>();
        line.add(command.get());
        arguments.ifPresent(values -> line.addAll(List.of(values)));
        return Optional.of(List.copyOf(line));
    }

    private static boolean isBareJava(final String command) {
        final String name = fileName(command).toLowerCase(Locale.ROOT);
        final String bare = name.endsWith(EXE) ? name.substring(0, name.length() - EXE.length()) : name;
        return BARE_JAVA.contains(bare);
    }

    private static String fileName(final String command) {
        final int slash = Math.max(command.lastIndexOf('/'), command.lastIndexOf('\\'));
        return command.substring(slash + 1);
    }
}

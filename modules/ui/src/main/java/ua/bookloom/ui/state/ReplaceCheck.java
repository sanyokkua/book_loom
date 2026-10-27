package ua.bookloom.ui.state;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.ui.dialog.ConfirmPrompt;

/**
 * Asks before an export replaces files: when the book or a chosen side file exists — looked for off the FX thread as
 * the export starts, before any model call — the person is asked, every occupied path is named, and only Replace starts
 * the export again allowing the overwrite. Cancel writes nothing. FX thread only.
 */
@Slf4j
final class ReplaceCheck {

    private final ConfirmPrompt confirm;

    ReplaceCheck(final ConfirmPrompt confirm) {
        this.confirm = Objects.requireNonNull(confirm, "confirm");
    }

    /**
     * Asks whether the existing files may be replaced.
     *
     * @param taken the existing files among those the export writes, the book first; not empty
     * @param replace starts the export allowing the overwrite, run only on Replace
     */
    void ask(final List<Path> taken, final Runnable replace) {
        Objects.requireNonNull(taken, "taken");
        Objects.requireNonNull(replace, "replace");
        log.info("replace asked for {} paths", taken.size());
        log.debug("replace asked for {}", taken);
        final String paths = taken.stream().map(Path::toString).collect(Collectors.joining("\n"));
        confirm.ask(
                ConfirmPrompt.Question.REPLACE_EXISTING, List.of(taken.size()), List.of(taken.size(), paths), () -> {
                    log.info("replace answered yes for {} paths", taken.size());
                    replace.run();
                });
    }
}

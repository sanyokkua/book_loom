package ua.bookloom.ui.state;

import java.nio.file.Path;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/** Words the refusal beside Save to: the path rules first, then an occupied path the disk check found. */
// Checkstyle parses source before Lombok runs, so it cannot see the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ExportRefusals {

    /**
     * Picks the refusal that stands.
     *
     * @param messages the catalogue the text comes from
     * @param book the open book, or null when none is open
     * @param destination the destination as a path, empty when it names none
     * @param occupied the first existing file among the book and the chosen side files, or null when none
     * @param justExported the file the last export wrote and whose result is still shown, or null when none
     * @return the refusal text; empty when nothing is refused
     */
    static String text(
            final Messages messages,
            final @Nullable OpenedBook book,
            final Optional<Path> destination,
            final @Nullable Path occupied,
            final @Nullable Path justExported) {
        if (book == null || destination.isEmpty()) {
            return "";
        }
        final Optional<ExportPathRules.Refusal> rule = ExportPathRules.refusal(book.source(), destination.get());
        if (rule.isPresent()) {
            return messages.get(
                    switch (rule.get()) {
                        case SOURCE_ITSELF -> MessageKey.EXPORT_REFUSAL_SOURCE;
                        case CHANGED_TYPE -> MessageKey.EXPORT_REFUSAL_TYPE;
                    });
        }
        if (occupied == null) {
            return "";
        }
        if (occupied.equals(justExported)) {
            return messages.get(MessageKey.EXPORT_REFUSAL_JUST_EXPORTED);
        }
        return messages.get(MessageKey.EXPORT_REFUSAL_OCCUPIED, occupied.toString());
    }
}

package ua.bookloom.ui.state;

import java.nio.file.Path;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * Words what stands beside Save to: a refusal from the path rules first, then an occupied path the disk check found —
 * unless the occupied file is the one the shown result just wrote, which is said as a neutral note, since nothing went
 * wrong and the success lines above must not sit beside an error.
 */
// Checkstyle parses source before Lombok runs, so it cannot see the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ExportRefusals {

    /**
     * What stands beside Save to.
     *
     * @param refusal the error text, empty when nothing is refused
     * @param note the neutral text about the file just exported, empty otherwise
     */
    record Verdict(String refusal, String note) {

        static final Verdict NONE = new Verdict("", "");

        /** Whether Export book must wait: something is refused, or the file was just written and Replace is off. */
        boolean isBlocking() {
            return !refusal.isEmpty() || !note.isEmpty();
        }
    }

    /**
     * Picks what stands.
     *
     * @param messages the catalogue the text comes from
     * @param book the open book, or null when none is open
     * @param destination the destination as a path, empty when it names none
     * @param occupied the first existing file among the book and the chosen side files, or null when none
     * @param justExported the file the last export wrote and whose result is still shown, or null when none
     * @return the verdict; {@link Verdict#NONE} when nothing stands
     */
    static Verdict verdict(
            final Messages messages,
            final @Nullable OpenedBook book,
            final Optional<Path> destination,
            final @Nullable Path occupied,
            final @Nullable Path justExported) {
        if (book == null || destination.isEmpty()) {
            return Verdict.NONE;
        }
        final Optional<ExportPathRules.Refusal> rule = ExportPathRules.refusal(book.source(), destination.get());
        if (rule.isPresent()) {
            return new Verdict(
                    messages.get(
                            switch (rule.get()) {
                                case SOURCE_ITSELF -> MessageKey.EXPORT_REFUSAL_SOURCE;
                                case CHANGED_TYPE -> MessageKey.EXPORT_REFUSAL_TYPE;
                            }),
                    "");
        }
        if (occupied == null) {
            return Verdict.NONE;
        }
        if (occupied.equals(justExported)) {
            return new Verdict("", messages.get(MessageKey.EXPORT_NOTE_JUST_EXPORTED));
        }
        return new Verdict(messages.get(MessageKey.EXPORT_REFUSAL_OCCUPIED, occupied.toString()), "");
    }

    /**
     * Words what an export waits for: a translating run asks for a pause, any other model work only for patience.
     *
     * @param messages the catalogue the text comes from
     * @param blocker the running work that rules out an export, or null when none does
     * @return the note, empty when nothing is waited for; the export's own work is not a wait
     */
    static String waitNote(final Messages messages, final @Nullable ActivityKind blocker) {
        if (blocker == null || blocker == ActivityKind.EXPORT) {
            return "";
        }
        return blocker == ActivityKind.TRANSLATION
                ? messages.get(MessageKey.EXPORT_NOTE_PAUSE)
                : messages.get(MessageKey.ACTIVITY_BLOCKED, messages.get(blocker.label()));
    }
}

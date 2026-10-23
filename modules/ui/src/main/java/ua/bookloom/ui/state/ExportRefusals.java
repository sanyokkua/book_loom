package ua.bookloom.ui.state;

import java.nio.file.Path;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * Words what stands beside Save to and beside Export book. Beside Save to: a refusal from the path rules, or a neutral
 * note — no destination yet, or the file the shown result just wrote, which exporting again asks before replacing. An
 * existing file is no refusal: the export asks before replacing it. Beside Export book: why it cannot be pressed now.
 */
// Checkstyle parses source before Lombok runs, so it cannot see the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ExportRefusals {

    /**
     * What stands beside Save to.
     *
     * @param refusal the error text, empty when nothing is refused
     * @param note the neutral text, empty otherwise
     * @param blocks whether Export book must wait
     */
    record Verdict(String refusal, String note, boolean blocks) {

        static final Verdict NONE = new Verdict("", "", false);

        /** Whether Export book must wait: something is refused, or no destination is chosen. */
        boolean isBlocking() {
            return blocks;
        }
    }

    /**
     * Picks what stands.
     *
     * @param messages the catalogue the text comes from
     * @param book the open book, or null when none is open
     * @param destination the destination as a path, empty when it names none
     * @param bookTaken the destination file exists, as the disk check last answered
     * @param justExported the file the last export wrote and whose result is still shown, or null when none
     * @return the verdict; {@link Verdict#NONE} when nothing stands
     */
    static Verdict verdict(
            final Messages messages,
            final @Nullable OpenedBook book,
            final Optional<Path> destination,
            final boolean bookTaken,
            final @Nullable Path justExported) {
        if (book == null) {
            return Verdict.NONE;
        }
        if (destination.isEmpty()) {
            // Not a refusal: nothing is wrong yet, but Export book waits for a path and should say so.
            return new Verdict("", messages.get(MessageKey.EXPORT_NOTE_NO_DESTINATION), true);
        }
        final Optional<ExportPathRules.Refusal> rule = ExportPathRules.refusal(book.source(), destination.get());
        if (rule.isPresent()) {
            return new Verdict(
                    messages.get(
                            switch (rule.get()) {
                                case SOURCE_ITSELF -> MessageKey.EXPORT_REFUSAL_SOURCE;
                                case CHANGED_TYPE -> MessageKey.EXPORT_REFUSAL_TYPE;
                            }),
                    "",
                    true);
        }
        if (bookTaken && destination.get().equals(justExported)) {
            return new Verdict("", messages.get(MessageKey.EXPORT_NOTE_JUST_EXPORTED), false);
        }
        return Verdict.NONE;
    }

    /**
     * Words why Export book cannot be pressed now.
     *
     * @param messages the catalogue the text comes from
     * @param blocker the running work that rules out an export, or null when none does
     * @param figures the project's figures, or null while they are not known
     * @return the reason, or null when nothing beside the destination stops an export
     */
    static @Nullable String blockReason(
            final Messages messages, final @Nullable ActivityKind blocker, final @Nullable ExportFigures figures) {
        if (blocker != null && blocker != ActivityKind.EXPORT) {
            return waitNote(messages, blocker);
        }
        if (figures != null && figures.isNothing()) {
            return messages.get(MessageKey.EXPORT_BANNER_NOTHING);
        }
        return null;
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

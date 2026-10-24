package ua.bookloom.ui.state;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.ui.i18n.LanguageNames;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ImportVerdict.Kind;

/** Words the one verdict line of each {@link ImportState}, without a scene so each sentence can be tested alone. */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public final class ImportVerdicts {

    private static final LanguageNames NAMES = new LanguageNames();
    private static final String NAME_SEPARATOR = ", ";

    /**
     * The line for a state.
     *
     * @param messages the catalogue the sentence comes from
     * @param state what the import page shows
     * @return the line, or empty for {@link ImportState.Idle}, which says nothing
     */
    public static Optional<ImportVerdict> of(final Messages messages, final ImportState state) {
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(state, "state");
        final Optional<ImportVerdict> verdict =
                switch (state) {
                    case ImportState.Idle idle -> Optional.empty();
                    case ImportState.Opening opening ->
                        line(Kind.READING, messages.get(MessageKey.IMPORT_VERDICT_READING));
                    case ImportState.Detected detected -> detected(messages, detected);
                    case ImportState.DrmBlocked blocked -> line(Kind.DRM, drm(messages, blocked));
                    case ImportState.Unsupported unsupported ->
                        line(Kind.UNSUPPORTED, unsupported(messages, unsupported));
                    case ImportState.Refused refused -> line(Kind.DAMAGED, damaged(messages, refused.fileName()));
                    case ImportState.NoText empty ->
                        line(Kind.NO_TEXT, messages.get(MessageKey.IMPORT_VERDICT_NO_TEXT));
                    case ImportState.AlreadyInProjects already ->
                        line(
                                Kind.ALREADY,
                                messages.get(
                                        MessageKey.IMPORT_VERDICT_ALREADY,
                                        String.join(NAME_SEPARATOR, already.projectNames())));
                };
        verdict.ifPresent(
                found -> log.debug("verdict for {} is {}", state.getClass().getSimpleName(), found.kind()));
        return verdict;
    }

    private static Optional<ImportVerdict> line(final Kind kind, final String text) {
        return Optional.of(new ImportVerdict(kind, text));
    }

    private static Optional<ImportVerdict> detected(final Messages messages, final ImportState.Detected detected) {
        final BookCard card = detected.card();
        final LanguageWarning warning = detected.warning();
        if (warning instanceof LanguageWarning.Mismatch mismatch) {
            return line(
                    Kind.TWO_MARKS,
                    messages.get(
                            MessageKey.IMPORT_VERDICT_TWO_MARKS,
                            NAMES.nameOf(mismatch.declared(), messages.locale()),
                            NAMES.nameOf(mismatch.content(), messages.locale())));
        }
        if (warning instanceof LanguageWarning.Unrecognized unrecognized) {
            return line(Kind.UNRECOGNIZED, messages.get(MessageKey.IMPORT_UNRECOGNIZED_TEXT, unrecognized.rawCode()));
        }
        return line(
                Kind.READY,
                messages.get(MessageKey.IMPORT_VERDICT_READY, card.chapters(), WordCount.text(messages, card.words())));
    }

    private static String unsupported(final Messages messages, final ImportState.Unsupported unsupported) {
        final String type = unsupported.detectedType();
        log.debug("unsupported line names the file's type: {}", type != null);
        return type == null
                ? messages.get(MessageKey.IMPORT_VERDICT_UNSUPPORTED_UNKNOWN, unsupported.fileName())
                : messages.get(MessageKey.IMPORT_VERDICT_UNSUPPORTED, unsupported.fileName(), type);
    }

    // The name promised a format the content is not, so the line says which format it could not be read as.
    private static String damaged(final Messages messages, final String fileName) {
        final Optional<BookFormat> promised = BookFormat.ofFileName(fileName);
        log.debug("damaged line names the promised format: {}", promised.orElse(null));
        return promised.map(format -> messages.get(
                        MessageKey.IMPORT_VERDICT_DAMAGED_AS,
                        fileName,
                        format.name().toLowerCase(Locale.ROOT)))
                .orElseGet(() -> messages.get(MessageKey.IMPORT_VERDICT_DAMAGED, fileName));
    }

    private static String drm(final Messages messages, final ImportState.DrmBlocked blocked) {
        final String locked = messages.get(MessageKey.IMPORT_VERDICT_DRM, blocked.fileName());
        final String scheme = blocked.scheme();
        return scheme == null ? locked : locked + " " + messages.get(MessageKey.IMPORT_VERDICT_DRM_SCHEME, scheme);
    }
}

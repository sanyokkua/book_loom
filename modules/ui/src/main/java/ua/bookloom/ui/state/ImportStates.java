package ua.bookloom.ui.state;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.BookInspection;
import ua.bookloom.api.document.BookProfile;
import ua.bookloom.api.document.BookStats;
import ua.bookloom.api.document.LanguageEvidence;
import ua.bookloom.api.pipeline.ImportedBook;

/** Maps what an import answered onto the state the import screen shows. */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public final class ImportStates {

    /**
     * Chooses the state for an answered import from the inspection's verdict and its language evidence.
     *
     * @param fileName the name of the imported file, without its directory
     * @param imported the answer; a readable book carries its format, and a project when it was stored
     * @return {@link ImportState.Detected} for a stored book, {@link ImportState.DrmBlocked} or
     *     {@link ImportState.Unsupported} for the verdicts that refuse it, and {@link ImportState.Refused} for a
     *     readable book that no project was created for; never null
     */
    public static ImportState of(final String fileName, final ImportedBook imported) {
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(imported, "imported");
        final BookInspection inspection = imported.inspection();
        log.debug(
                "choosing the import state of {}: verdict {}, language evidence {}, project stored {}",
                fileName,
                inspection.verdict(),
                inspection.languageEvidence().verdict(),
                imported.projectId() != null);
        return switch (inspection.verdict()) {
            case DRM_PROTECTED -> new ImportState.DrmBlocked(fileName, inspection.encryptionScheme());
            case UNSUPPORTED -> new ImportState.Unsupported(fileName, inspection.detectedType());
            case READABLE -> imported.projectId() == null ? notStored(fileName) : detected(fileName, imported);
        };
    }

    private static ImportState notStored(final String fileName) {
        return new ImportState.Refused(
                fileName,
                AppError.of(ErrorCode.validation, "This book could not be opened", "No project was created for it."));
    }

    private static ImportState detected(final String fileName, final ImportedBook imported) {
        final BookCard card = cardOf(fileName, imported.inspection(), imported.profile());
        log.trace("card of {}: title {}, author {}", fileName, card.title(), card.author());
        return new ImportState.Detected(card, warningOf(imported.inspection().languageEvidence()));
    }

    private static BookCard cardOf(
            final String fileName, final BookInspection inspection, final @Nullable BookProfile profile) {
        final BookFormat format = Objects.requireNonNull(inspection.format(), "format");
        final String declared = declaredOrNull(inspection.languageEvidence().declared());
        if (profile == null) {
            return new BookCard(fileName, format, inspection.formatVersion(), null, null, declared, 0, 0, 0, 0, null);
        }
        final BookStats stats = profile.stats();
        return new BookCard(
                fileName,
                format,
                inspection.formatVersion(),
                declaredOrNull(profile.title()),
                declaredOrNull(profile.author()),
                declared,
                profile.structure().size(),
                stats.words(),
                stats.images(),
                stats.fonts(),
                profile.cover());
    }

    private static @Nullable LanguageWarning warningOf(final LanguageEvidence evidence) {
        final String declared = evidence.declared();
        final String content = evidence.contentMajority();
        final String raw = evidence.declaredRaw();
        return switch (evidence.verdict()) {
            case MISMATCH ->
                declared == null || content == null ? null : new LanguageWarning.Mismatch(declared, content);
            case UNRECOGNIZED -> raw == null ? null : new LanguageWarning.Unrecognized(raw);
            case MATCH, ABSENT -> null;
        };
    }

    /** A blank value is no declaration: the row is omitted rather than shown empty. */
    private static @Nullable String declaredOrNull(final @Nullable String value) {
        return value == null || value.isBlank() ? null : value;
    }
}

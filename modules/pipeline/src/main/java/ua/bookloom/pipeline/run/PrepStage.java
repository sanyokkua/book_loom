package ua.bookloom.pipeline.run;

import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.pipeline.glossary.FrequencyScan;
import ua.bookloom.pipeline.prompt.StyleSheet;

/**
 * What a run settles before its first model call: the style sheet, derived once from the brief as it is now, and the
 * names of a book whose glossary is empty. The names come from the deterministic scan, which needs no network; the
 * model scan runs only when the person asks for it on Names &amp; style, never on its own.
 */
// Checkstyle parses source text before Lombok's annotation processor creates the private constructor,
// so suppress only its source-level utility-constructor false positive.
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public final class PrepStage {

    /**
     * What preparation settled.
     *
     * @param styleSheet the style sheet every call of the run carries
     * @param scanned whether the glossary was empty, so the scan ran
     * @param proposed how many names the scan added; 0 when it did not run
     */
    public record Prepared(StyleSheet styleSheet, boolean scanned, int proposed) {

        /** Rejects a missing style sheet. */
        public Prepared {
            Objects.requireNonNull(styleSheet, "styleSheet");
        }
    }

    /**
     * Prepares one run.
     *
     * @param glossary the non-null glossary the names are read from and proposed into
     * @param projectId the non-null id of the project the run translates
     * @param brief the non-null brief as the run starts with it
     * @param document the non-null opened book, whose body units are scanned
     * @return what preparation settled, or the glossary's error
     */
    public static Result<Prepared> prepare(
            final GlossaryRepository glossary, final String projectId, final BookBrief brief, final Document document) {
        Objects.requireNonNull(glossary, "glossary");
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(brief, "brief");
        Objects.requireNonNull(document, "document");
        try {
            log.debug("Preparing run project={} dial={}", projectId, brief.dial());
            final StyleSheet styleSheet = StyleSheet.from(brief);
            final Result<Prepared> prepared = glossary.all(projectId)
                    .flatMap(held -> scanIfEmpty(
                            glossary, projectId, document, sourceLanguageOf(brief, document), held, styleSheet));
            if (prepared.isOk()) {
                logPrepared(projectId, Objects.requireNonNull(prepared.data(), "prepared"));
            }
            return prepared;
        } catch (Throwable cause) {
            log.error("Unexpected failure while preparing a run project={}", projectId, cause);
            return Result.err(AppError.of(
                    ErrorCode.internal,
                    "This run could not be prepared",
                    "An unexpected error stopped the run before it began.",
                    null,
                    cause));
        }
    }

    private static Result<Prepared> scanIfEmpty(
            final GlossaryRepository glossary,
            final String projectId,
            final Document document,
            @Nullable final String sourceLanguage,
            final List<GlossaryEntry> held,
            final StyleSheet styleSheet) {
        if (!held.isEmpty()) {
            log.debug("Name scan skipped project={}: the glossary holds {} entries", projectId, held.size());
            return Result.ok(new Prepared(styleSheet, false, 0));
        }
        log.debug("Name scan runs project={}: the glossary is empty", projectId);
        return FrequencyScan.newTerms(projectId, bodySegments(document), sourceLanguage, glossary)
                .flatMap(proposals -> addAll(glossary, proposals))
                .map(added -> new Prepared(styleSheet, true, added));
    }

    private static @Nullable String sourceLanguageOf(final BookBrief brief, final Document document) {
        return brief.sourceLanguage() == null ? document.declaredLang() : brief.sourceLanguage();
    }

    private static Result<Integer> addAll(final GlossaryRepository glossary, final List<GlossaryEntry> proposals) {
        for (final GlossaryEntry proposal : proposals) {
            final Result<GlossaryEntry> added = glossary.add(proposal);
            if (added.isErr()) {
                return Result.err(Objects.requireNonNull(added.error(), "error"));
            }
            log.trace("Proposed name term={}", proposal.term());
        }
        return Result.ok(proposals.size());
    }

    private static List<Segment> bodySegments(final Document document) {
        return document.units().stream()
                .filter(unit -> !unit.isAuxiliary())
                .flatMap(unit -> unit.segments().stream())
                .toList();
    }

    private static void logPrepared(final String projectId, final Prepared prepared) {
        log.info(
                "Prepared run project={} styleSheetHash={} glossaryScanned={} namesProposed={}",
                projectId,
                prepared.styleSheet().hash(),
                prepared.scanned(),
                prepared.proposed());
    }
}

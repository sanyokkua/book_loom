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
import ua.bookloom.api.persistence.LexiconRepository;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.pipeline.glossary.FrequencyScan;
import ua.bookloom.pipeline.glossary.KeyTermScan;
import ua.bookloom.pipeline.prompt.StyleSheet;

/**
 * What a run settles before its first model call: the style sheet, derived once from the brief as it is now, and the
 * names of a book whose glossary is empty, and its recurring terms when the lexicon is empty. Both come from
 * deterministic scans, which need no network; the model scan runs only when the person asks for it on Names &amp;
 * style, never on its own.
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
     * @param lexicon the non-null lexicon the recurring terms are proposed into when it is empty
     * @param projectId the non-null id of the project the run translates
     * @param brief the non-null brief as the run starts with it
     * @param document the non-null opened book, whose body units are scanned
     * @return what preparation settled, or the glossary's error
     */
    public static Result<Prepared> prepare(
            final GlossaryRepository glossary,
            final LexiconRepository lexicon,
            final String projectId,
            final BookBrief brief,
            final Document document) {
        Objects.requireNonNull(glossary, "glossary");
        Objects.requireNonNull(lexicon, "lexicon");
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(brief, "brief");
        Objects.requireNonNull(document, "document");
        try {
            log.debug("Preparing run project={} dial={}", projectId, brief.dial());
            final StyleSheet styleSheet = StyleSheet.from(brief);
            final Result<Prepared> prepared = glossary.all(projectId)
                    .flatMap(held -> scanIfEmpty(
                            glossary, projectId, document, sourceLanguageOf(brief, document), held, styleSheet))
                    .flatMap(done -> scanKeyTermsIfEmpty(
                                    glossary, lexicon, projectId, document, sourceLanguageOf(brief, document))
                            .map(terms -> done));
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

    private static Result<Integer> scanKeyTermsIfEmpty(
            final GlossaryRepository glossary,
            final LexiconRepository lexicon,
            final String projectId,
            final Document document,
            @Nullable final String sourceLanguage) {
        return lexicon.all(projectId).flatMap(held -> {
            if (!held.isEmpty()) {
                log.debug("Key-term scan skipped project={}: the lexicon holds {} terms", projectId, held.size());
                return Result.ok(0);
            }
            return KeyTermScan.newTerms(projectId, bodySegments(document), sourceLanguage, glossary, lexicon)
                    .flatMap(proposals -> putAll(lexicon, proposals));
        });
    }

    private static Result<Integer> putAll(final LexiconRepository lexicon, final List<LexiconEntry> proposals) {
        for (final LexiconEntry proposal : proposals) {
            final Result<LexiconEntry> stored = lexicon.put(proposal);
            if (stored.isErr()) {
                return Result.err(Objects.requireNonNull(stored.error(), "error"));
            }
        }
        log.info("Key terms proposed count={}", proposals.size());
        return Result.ok(proposals.size());
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
        return FrequencyScan.storyText(document);
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

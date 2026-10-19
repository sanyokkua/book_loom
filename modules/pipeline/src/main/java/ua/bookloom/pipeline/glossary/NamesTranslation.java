package ua.bookloom.pipeline.glossary;

import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.pipeline.GlossaryReviewReport;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.pipeline.glossary.GlossaryModelScans.Book;
import ua.bookloom.pipeline.glossary.TermReview.Reviewed;
import ua.bookloom.pipeline.prompt.ModelCalls;

/**
 * The names pass's translate step for the glossary (15i.N8): a rendering for every entry the model may still change,
 * asked with the held glossary, the readings and the verified names of the names step, and written the way a review
 * writes its suggestions — locked by {@link LockRule} through the facts {@link SuggestionLocks} gave each suggestion, so
 * a review and the names pass share one lock rule and one meaning of a verified name. Only an entry that was open is
 * touched, so a locked entry or one with the person's target is never suggested over.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class NamesTranslation {

    /**
     * Suggests and writes, locking where the rule says.
     *
     * @param glossary the glossary the pass edits; never null
     * @param scans the model actions of the glossary, which ask for the renderings; never null
     * @param projectId the project whose glossary is edited; never null
     * @param book what the actions run on; never null
     * @param reviewed what the names step left: the readings of the entries read and the ids verified as names; never
     *     null
     * @param calls the seam every model call goes through; never null
     * @return the report of what was suggested and the glossary after, or the first failed call's or glossary access's
     *     error
     */
    static Result<GlossaryReviewReport> run(
            final GlossaryRepository glossary,
            final GlossaryModelScans scans,
            final String projectId,
            final Book book,
            final Reviewed reviewed,
            final ModelCalls calls) {
        Objects.requireNonNull(glossary, "glossary");
        Objects.requireNonNull(scans, "scans");
        Objects.requireNonNull(reviewed, "reviewed");
        return glossary.all(projectId).flatMap(held -> {
            final List<GlossaryEntry> open =
                    held.stream().filter(TermReview::isOpen).toList();
            log.debug(
                    "Names translation project={} held={} open={} verified={}",
                    projectId,
                    held.size(),
                    open.size(),
                    reviewed.verified().size());
            if (open.isEmpty()) {
                return Result.ok(new GlossaryReviewReport(0, 0, held));
            }
            final TargetContext context = new TargetContext(held, reviewed.readings(), reviewed.verified());
            return scans.suggestFor(open, context, book, calls)
                    .flatMap(suggestions -> ReviewCommit.apply(
                            glossary,
                            projectId,
                            List.of(),
                            suggestions,
                            book.frame().analysisLanguage(),
                            book.story().visibleTexts()));
        });
    }
}

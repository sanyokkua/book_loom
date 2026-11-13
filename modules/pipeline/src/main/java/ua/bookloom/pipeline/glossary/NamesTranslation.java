package ua.bookloom.pipeline.glossary;

import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;
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
 * touched, so a locked entry or one with the person's target is never suggested over. With the suggestions written, a
 * scanned form that fits two new names joins one of them when their targets agree ({@link FormCollapse}), and a name
 * the book plays on is marked for the once-per-name question ({@link MeaningQuestions}).
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
     * @param rejected receives the count of suggested renderings the target's rules refused; never null
     * @return the report of what was suggested and the glossary after, or the first failed call's or glossary access's
     *     error
     */
    static Result<GlossaryReviewReport> run(
            final GlossaryRepository glossary,
            final GlossaryModelScans scans,
            final String projectId,
            final Book book,
            final Reviewed reviewed,
            final ModelCalls calls,
            final IntConsumer rejected) {
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
            final String language = book.frame().analysisLanguage();
            return scans.suggestFor(open, context, book, calls, rejected)
                    .flatMap(suggestions -> ReviewCommit.apply(
                            glossary,
                            projectId,
                            new ReviewCommit.Answers(List.of(), List.of(), suggestions),
                            language,
                            book.story().visibleTexts()))
                    .flatMap(report -> afterSuggestions(glossary, projectId, book, reviewed)
                            .map(marked -> report));
        });
    }

    // With the suggested targets written, a form two new names share can be folded, and the names to ask are marked.
    private static Result<Integer> afterSuggestions(
            final GlossaryRepository glossary, final String projectId, final Book book, final Reviewed reviewed) {
        return FormCollapse.collapse(glossary, projectId, book.frame().analysisLanguage())
                .flatMap(collapsed -> MeaningQuestions.mark(glossary, projectId, reviewed.readings(), book.names()));
    }
}

package ua.bookloom.pipeline.glossary;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.pipeline.glossary.GlossaryModelScans.Book;
import ua.bookloom.pipeline.glossary.TargetValidator.Verdict;

/**
 * The names pass's check step: every staged suggestion is read against the target language's rules so the pass can
 * say how many would pass Accept all and how many it would leave. Nothing is changed and nothing is accepted, because
 * accepting is the person's act and a suggestion stays "likely" until they press it.
 */
@Slf4j
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SuggestionCheck {

    /**
     * What the check found.
     *
     * @param checked suggested entries with a target that were read
     * @param passing those no rule refuses and no review flag marks
     */
    record Counts(int checked, int passing) {

        int leftForReview() {
            return checked - passing;
        }
    }

    static Counts of(final String projectId, final Book book, final List<GlossaryEntry> held) {
        final List<GlossaryEntry> suggested = held.stream()
                .filter(entry -> entry.isSuggested()
                        && entry.target() != null
                        && !entry.target().isBlank())
                .toList();
        log.debug("Check suggestions project={} held={} suggested={}", projectId, held.size(), suggested.size());
        final TargetValidator validator = TargetValidator.of(
                book.frame().targetLanguage(),
                book.frame().analysisLanguage(),
                book.names(),
                new TargetContext(held, Map.of()),
                book.conventions());
        final int passing = (int) suggested.stream()
                .filter(entry -> passes(validator, entry, book.frame().targetLanguage()))
                .count();
        final Counts counts = new Counts(suggested.size(), passing);
        log.info(
                "Names pass project={} checked {} suggestion(s): {} pass, {} left for review",
                projectId,
                counts.checked(),
                counts.passing(),
                counts.leftForReview());
        return counts;
    }

    // Accept all cleans stress marks before it judges, so the check reads the same text.
    private static boolean passes(final TargetValidator validator, final GlossaryEntry entry, final String language) {
        final String clean =
                Objects.requireNonNull(TargetText.clean(String.valueOf(entry.target()), language), "clean");
        final Verdict verdict = validator.validate(entry, clean);
        return verdict.passes();
    }
}

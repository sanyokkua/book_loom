package ua.bookloom.pipeline.lexicon;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.LexiconRepository;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.glossary.GlossaryIds;

/**
 * The model's suggested renderings of recurring terms, asked through the glossary's suggestion call: the terms go out
 * as glossary entries of type term, and each answered target comes back as the term's suggestion, already without the
 * stress marks its language asks to remove (the suggestion call cleans every target it reads).
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class LexiconSuggestions {

    /** The terms as the glossary entries the suggestion call reads, in their order. */
    static List<GlossaryEntry> asGlossary(final String projectId, final List<LexiconEntry> entries) {
        return entries.stream()
                .map(entry -> GlossaryEntry.of(
                        GlossaryIds.of(projectId, entry.term()),
                        projectId,
                        entry.term(),
                        null,
                        TermType.TERM,
                        Gender.UNKNOWN,
                        false))
                .toList();
    }

    /**
     * Stores each answered target as its term's suggestion.
     *
     * @param lexicon where the terms are stored
     * @param projectId the project
     * @param open the terms asked, in the order of {@code answered}
     * @param answered the entries as answered, one per asked term
     * @return the lexicon as it now stands, or the first store error
     */
    static Result<List<LexiconEntry>> store(
            final LexiconRepository lexicon,
            final String projectId,
            final List<LexiconEntry> open,
            final List<GlossaryEntry> answered) {
        int stored = 0;
        for (int i = 0; i < open.size(); i++) {
            final String target = answered.get(i).target();
            if (target != null && !target.isBlank()) {
                final Result<Optional<LexiconEntry>> updated =
                        lexicon.update(projectId, open.get(i).term(), found -> found.withSuggested(target));
                if (updated.isErr()) {
                    return Result.err(Objects.requireNonNull(updated.error(), "error"));
                }
                stored++;
            }
        }
        log.debug("Lexicon suggestions project={} asked={} stored={}", projectId, open.size(), stored);
        return lexicon.all(projectId);
    }
}

package ua.bookloom.pipeline.glossary;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.pipeline.UnknownGender;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.NameKnowledge;
import ua.bookloom.api.project.Origin;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.index.BookIndex;

/**
 * Counts how often the body text names each character whose gender is still unknown or only a rule's or the model's,
 * so Start asks only about those the book keeps coming back to, the least confident first, each with its held value
 * offered. A mention is the whole name as a separate word run; a possessive ending still counts.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class CharacterMentions {

    private static final String LETTER_OR_DIGIT = "[\\p{L}\\p{M}\\p{N}]";
    // A likely gender with no recorded confidence, or a rule the model disagrees with, sits between none and sure.
    private static final double HALF_SURE = 0.5;

    static Result<List<UnknownGender>> read(
            final GlossaryRepository glossary,
            @Nullable final BookIndex index,
            final String projectId,
            final int minMentions) {
        if (index == null) {
            log.debug("Unknown genders project={}: no book is open, so nothing is counted", projectId);
            return Result.ok(List.of());
        }
        return glossary.all(projectId).map(held -> unknown(held, index, Math.max(1, minMentions)));
    }

    /**
     * The characters to ask about, the least confident first and then the most named: none at all first, then a
     * likely gender by the model's confidence, a rule the model disagrees with as half sure, and a rule it agrees with
     * last.
     */
    static List<UnknownGender> unknown(
            final List<GlossaryEntry> entries, final BookIndex index, final int minMentions) {
        final String body = String.join("\n", index.visibleTexts(index.story()));
        final List<UnknownGender> found = entries.stream()
                .filter(entry -> entry.type() == TermType.CHARACTER
                        && (entry.gender() == Gender.UNKNOWN || entry.isGenderSuggested()))
                .map(entry -> questionOf(entry, count(entry.term(), body)))
                .filter(candidate -> candidate.mentions() >= minMentions)
                .sorted(Comparator.comparingDouble(CharacterMentions::sureness)
                        .thenComparing(
                                Comparator.comparingInt(UnknownGender::mentions).reversed())
                        .thenComparing(UnknownGender::term))
                .toList();
        log.debug(
                "Unknown genders: {} of {} entries have {} or more mentions",
                found.size(),
                entries.size(),
                minMentions);
        return found;
    }

    private static UnknownGender questionOf(final GlossaryEntry entry, final int mentions) {
        final NameKnowledge known = entry.knowledge();
        final Double confidence =
                known != null && known.gender() == entry.gender() && entry.genderOrigin() == Origin.LIKELY
                        ? known.confidence()
                        : null;
        return new UnknownGender(
                entry.term(),
                mentions,
                entry.isGenderSuggested(),
                entry.id(),
                entry.gender(),
                entry.isGenderSuggested() ? entry.genderOrigin() : null,
                confidence,
                known != null && known.disagreesWithRule(entry.gender(), entry.genderOrigin()) ? known.gender() : null,
                entry.target());
    }

    // How sure the held value is, for the order of the question.
    private static double sureness(final UnknownGender character) {
        final Origin origin = character.origin();
        if (origin == null) {
            return 0.0;
        }
        if (origin == Origin.LIKELY) {
            return Objects.requireNonNullElse(character.confidence(), HALF_SURE);
        }
        return character.modelGender() == null ? 1.0 : HALF_SURE;
    }

    private static int count(final String term, final String body) {
        if (term.isBlank()) {
            return 0;
        }
        final Matcher matcher = Pattern.compile(
                        "(?<!" + LETTER_OR_DIGIT + ")" + Pattern.quote(term.strip()) + "(?!" + LETTER_OR_DIGIT + ")")
                .matcher(body);
        int mentions = 0;
        while (matcher.find()) {
            mentions++;
        }
        return mentions;
    }
}

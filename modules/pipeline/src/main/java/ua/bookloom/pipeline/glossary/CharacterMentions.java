package ua.bookloom.pipeline.glossary;

import java.util.Comparator;
import java.util.List;
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
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.index.BookIndex;

/**
 * Counts how often the body text names each character whose gender is still unknown or only suggested, so Start asks only about those
 * the book keeps coming back to. A mention is the whole name as a separate word run; a possessive ending still counts.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class CharacterMentions {

    private static final String LETTER_OR_DIGIT = "[\\p{L}\\p{M}\\p{N}]";

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

    /** Counts the mentions in the story text of the book's index. */
    static List<UnknownGender> unknown(
            final List<GlossaryEntry> entries, final BookIndex index, final int minMentions) {
        final String body = String.join("\n", index.visibleTexts(index.story()));
        final List<UnknownGender> found = entries.stream()
                .filter(entry -> entry.type() == TermType.CHARACTER
                        && (entry.gender() == Gender.UNKNOWN || entry.isGenderSuggested()))
                .map(entry -> new UnknownGender(entry.term(), count(entry.term(), body), entry.isGenderSuggested()))
                .filter(candidate -> candidate.mentions() >= minMentions)
                .sorted(Comparator.comparingInt(UnknownGender::mentions)
                        .reversed()
                        .thenComparing(UnknownGender::term))
                .toList();
        log.debug(
                "Unknown genders: {} of {} entries have {} or more mentions",
                found.size(),
                entries.size(),
                minMentions);
        return found;
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

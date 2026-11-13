package ua.bookloom.pipeline.proofread;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.pipeline.checks.TextSpan;

/**
 * The reach of a proofread rewrite for a mark that only a gender now known stands behind (design D15 "Reply"): the
 * rewrite may change the words the target language's gender check found wrong for that gender, and no other word, so
 * a fix of one person's verb never re-genders another person or rewords the sentence. A rewrite with another word
 * count, or another word changed, is out of reach. Punctuation is left to the never-worse vetoes.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class GenderScope {

    /** The refusal's stable name, which the desk shows before the word that broke it. */
    static final String VETO = "gender-scope";

    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{M}\\p{N}]++(?:['’ʼ-][\\p{L}\\p{M}\\p{N}]++)*+");

    /**
     * Whether a mark stands only on genders now known, so its rewrite is held to their wrong words.
     *
     * @param reasons the non-null reasons the segment was marked by
     * @return {@code true} if there is at least one reason and each names a gender now known, {@code false} otherwise
     */
    static boolean holds(final List<String> reasons) {
        Objects.requireNonNull(reasons, "reasons");
        return !reasons.isEmpty()
                && reasons.stream().allMatch(reason -> reason.startsWith(ArrivedGender.REASON_PREFIX));
    }

    /**
     * The first word the rewrite changes outside the wrong words.
     *
     * @param before the non-null plain text in place, which the spans are read on
     * @param after the non-null plain rewrite
     * @param wrong the non-null spans of {@code before} the gender check found wrong
     * @return the refusal naming the word if the rewrite reaches beyond the wrong words, or empty if it does not
     */
    static Optional<String> breach(final String before, final String after, final List<TextSpan> wrong) {
        Objects.requireNonNull(wrong, "wrong");
        final List<MatchResult> old =
                WORD.matcher(Objects.requireNonNull(before, "before")).results().toList();
        final List<String> fresh = WORD.matcher(Objects.requireNonNull(after, "after"))
                .results()
                .map(MatchResult::group)
                .toList();
        if (old.size() != fresh.size()) {
            log.debug("Gender scope breached: words {} -> {}", old.size(), fresh.size());
            return Optional.of(VETO + ": " + old.size() + " words became " + fresh.size());
        }
        for (int index = 0; index < old.size(); index++) {
            final MatchResult word = old.get(index);
            if (!word.group().equals(fresh.get(index)) && !isWrong(word, wrong)) {
                log.debug("Gender scope breached: word {} changed outside the wrong words", index);
                return Optional.of(VETO + ": " + word.group() + " → " + fresh.get(index));
            }
        }
        return Optional.empty();
    }

    private static boolean isWrong(final MatchResult word, final List<TextSpan> wrong) {
        return wrong.stream().anyMatch(span -> span.start() < word.end() && word.start() < span.end());
    }
}

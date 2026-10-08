package ua.bookloom.pipeline.reviewer;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.pipeline.DisplayText;
import ua.bookloom.pipeline.checks.TextChecks;
import ua.bookloom.pipeline.reviewer.Verification.FailureReason;
import ua.bookloom.pipeline.revision.RevisionGuards;

/**
 * What an edit did to the whole text, read from the candidate and the edited text alone: a doubled word, a changed
 * case at a sentence start, a letter of a foreign script, a changed quote, dash or sentence count, a re-inflected
 * glossary name and a swapped content word. A real run showed the reviewer's applied edits damaging the text about as
 * often as they improved it, so an edit that trips one of these is refused and the old text stays.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class EditGuards {

    /** A sentence start: the text start or a terminal mark, then opening marks, then the first letter. */
    private static final Pattern SENTENCE_START =
            Pattern.compile("(?:^|[.!?…][»”\"’)]*\\s+)[«„“\"'(\\[—–\\-\\s]*(\\p{L})");

    private static final Pattern WORD = Pattern.compile("\\p{L}[\\p{L}\\p{M}'’ʼ-]*");
    private static final int MIN_SWAP_WORD = 4;

    /**
     * The first guard the edited text trips.
     *
     * @param edit the edit as written; never null
     * @param candidate the text before the edit, masked; never null
     * @param edited the text after the edit, masked; never null
     * @param renderings the glossary renderings of the chunk; never null
     * @param functionWords the target language's function words; never null, may be empty
     * @return why the edit is refused, or empty when it passes every guard
     */
    static Optional<FailureReason> refusal(
            final ReviewEdit edit,
            final String candidate,
            final String edited,
            final Collection<String> renderings,
            final Set<String> functionWords) {
        final String before = DisplayText.of(candidate);
        final String after = DisplayText.of(edited);
        final Optional<FailureReason> reason = firstOf(edit, before, after, renderings, functionWords);
        log.debug("Edit guards criterion={} refusal={}", edit.criterion().wire(), reason.orElse(null));
        return reason;
    }

    private static Optional<FailureReason> firstOf(
            final ReviewEdit edit,
            final String before,
            final String after,
            final Collection<String> renderings,
            final Set<String> functionWords) {
        if (TextChecks.doubledWords(after) > TextChecks.doubledWords(before)) {
            return Optional.of(FailureReason.DOUBLED_WORD);
        }
        if (foreignLetters(after, before) > foreignLetters(before, before)) {
            return Optional.of(FailureReason.FOREIGN_SCRIPT);
        }
        if (changesCase(edit.criterion(), before, after)) {
            return Optional.of(FailureReason.CASE_CHANGED);
        }
        if (changesCounts(edit.criterion(), before, after)) {
            return Optional.of(FailureReason.COUNTS_CHANGED);
        }
        if (edit.criterion() != ReviewCriterion.TERMINOLOGY
                && CriterionFit.changesNameForm(
                        edit.quote().strip(), edit.replacement().strip(), renderings)) {
            return Optional.of(FailureReason.GLOSSARY_RENDERING);
        }
        if (edit.criterion() == ReviewCriterion.MEANING && swapsContentWord(edit, renderings, functionWords)) {
            return Optional.of(FailureReason.MEANING_SWAP);
        }
        return Optional.empty();
    }

    private static boolean changesCounts(final ReviewCriterion criterion, final String before, final String after) {
        if (criterion == ReviewCriterion.QUOTES) {
            return false;
        }
        return RevisionGuards.violationOfEdit(before, after, criterion == ReviewCriterion.OMISSION)
                .isPresent();
    }

    private static long foreignLetters(final String text, final String own) {
        final Character.UnicodeScript script = CriterionFit.majorityScript(own);
        return text.codePoints()
                .filter(Character::isLetter)
                .filter(letter -> Character.UnicodeScript.of(letter) != script)
                .count();
    }

    // A capital that appears in the middle of a sentence is a name, and a name is the glossary's business.
    private static boolean changesCase(final ReviewCriterion criterion, final String before, final String after) {
        final boolean lowered = lowerStarts(after) > lowerStarts(before);
        final boolean raised = criterion != ReviewCriterion.TERMINOLOGY && midCapitals(after) > midCapitals(before);
        return lowered || raised;
    }

    private static long lowerStarts(final String text) {
        final Matcher matcher = SENTENCE_START.matcher(text);
        long count = 0;
        while (matcher.find()) {
            if (Character.isLowerCase(text.codePointAt(matcher.start(1)))) {
                count++;
            }
        }
        return count;
    }

    private static long midCapitals(final String text) {
        final Matcher starts = SENTENCE_START.matcher(text);
        final Set<Integer> sentenceStarts = new HashSet<>();
        while (starts.find()) {
            sentenceStarts.add(starts.start(1));
        }
        return WORD.matcher(text)
                .results()
                .filter(word -> !sentenceStarts.contains(word.start()))
                .filter(word -> Character.isUpperCase(text.codePointAt(word.start())))
                .count();
    }

    // A word of the quote that has no counterpart in the replacement, replaced by a word that has none in the quote and
    // that no glossary rendering dictates: the icebreaker that became a glacier.
    private static boolean swapsContentWord(
            final ReviewEdit edit, final Collection<String> renderings, final Set<String> functionWords) {
        final List<String> quoted = contentWords(edit.quote(), functionWords);
        final List<String> replaced = contentWords(edit.replacement(), functionWords);
        final List<String> dropped = quoted.stream()
                .filter(word -> replaced.stream().noneMatch(other -> WordChange.sharesContentStem(word, other)))
                .toList();
        final List<String> added = replaced.stream()
                .filter(word -> quoted.stream().noneMatch(other -> WordChange.sharesContentStem(word, other)))
                .filter(word -> !dictated(word, renderings))
                .toList();
        return !dropped.isEmpty() && !added.isEmpty();
    }

    private static boolean dictated(final String word, final Collection<String> renderings) {
        return renderings.stream()
                .flatMap(rendering -> WordChange.words(rendering).stream())
                .anyMatch(known -> WordChange.sharesContentStem(word, known));
    }

    private static List<String> contentWords(final String text, final Set<String> functionWords) {
        return WordChange.words(text).stream()
                .filter(word -> word.length() >= MIN_SWAP_WORD && !functionWords.contains(word))
                .toList();
    }
}

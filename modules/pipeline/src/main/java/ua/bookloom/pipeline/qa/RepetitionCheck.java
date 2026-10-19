package ua.bookloom.pipeline.qa;

import static ua.bookloom.pipeline.qa.CheckName.REPETITION;

import java.util.Arrays;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The repetition check: catches a small local model's decode loop — a 3-word sequence repeated 3 or more times in a
 * row — without punishing an ordinary repeated phrase in prose.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class RepetitionCheck {

    private static final int WORDS_PER_SEQUENCE = 3;
    private static final int RUNS_TO_FAIL = 3;
    private static final int RUNS_TO_HALVE_MARGIN = 2;
    private static final double HALVED_MARGIN = 0.5;
    private static final double FULL_MARGIN = 1.0;

    static CheckResult run(final SoftCheckInput input) {
        Objects.requireNonNull(input, "input");
        final String[] words = input.targetDisplayText().trim().split("\\s+");
        final int longestRun = longestConsecutiveRun(words);
        if (longestRun >= RUNS_TO_FAIL) {
            return CheckResult.fail(REPETITION, "a 3-word sequence repeats " + longestRun + " times in a row");
        }
        return CheckResult.pass(REPETITION, longestRun == RUNS_TO_HALVE_MARGIN ? HALVED_MARGIN : FULL_MARGIN);
    }

    /** The longest run of consecutive, identical {@link #WORDS_PER_SEQUENCE}-word sequences in {@code words}. */
    private static int longestConsecutiveRun(final String[] words) {
        int longest = 0;
        for (int start = 0; start + WORDS_PER_SEQUENCE <= words.length; start++) {
            longest = Math.max(longest, runStartingAt(words, start));
        }
        return longest;
    }

    private static int runStartingAt(final String[] words, final int start) {
        int run = 1;
        int next = start + WORDS_PER_SEQUENCE;
        while (next + WORDS_PER_SEQUENCE <= words.length && sameSequence(words, start, next)) {
            run++;
            next += WORDS_PER_SEQUENCE;
        }
        return run;
    }

    private static boolean sameSequence(final String[] words, final int first, final int second) {
        return Arrays.equals(words, first, first + WORDS_PER_SEQUENCE, words, second, second + WORDS_PER_SEQUENCE);
    }
}

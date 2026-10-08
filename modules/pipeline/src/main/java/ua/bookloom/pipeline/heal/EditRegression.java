package ua.bookloom.pipeline.heal;

import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.pipeline.qa.QaResult;

/**
 * Whether the reviewer's edits, taken together, left the text worse than they found it. Each edit was verified alone,
 * but a damaging edit is worse than none, so the whole edited text is held to the same evaluation the draft passed.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class EditRegression {

    private static final double EPSILON = 1e-9;

    /**
     * Compares the evaluation of the edited text with that of the text before the edits.
     *
     * @param before the evaluation of the text before the edits; never null
     * @param after the evaluation of the edited text; never null
     * @return {@code true} when the edited text has a blocking finding the old one lacked, more of them, or a lower
     *     confidence
     */
    static boolean isWorse(final QaResult before, final QaResult after) {
        final Set<String> was = Blockers.of(before);
        final Set<String> now = Blockers.of(after);
        final boolean worse =
                !was.containsAll(now) || now.size() > was.size() || after.confidence() < before.confidence() - EPSILON;
        log.debug(
                "Edit regression blockers={}->{} confidence={}->{} worse={}",
                was,
                now,
                before.confidence(),
                after.confidence(),
                worse);
        return worse;
    }
}

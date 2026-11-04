package ua.bookloom.pipeline.qa;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.pipeline.DisplayText;

/**
 * The one code gate that keeps an edit only when no check got worse (design D14): every text the program changes
 * after its draft — the one repair, a proofread rewrite — goes through {@link #judge}. A candidate breaking a veto
 * ({@link NeverWorseVetoes}) is refused; otherwise it is kept when its score is not higher and it either clears the
 * finding that marked the text or scores strictly lower, or, for an unmarked paragraph of Max's whole-book re-read
 * only, rewrites few enough words. The score is a code count because no judge model is available at run time; its
 * weights and the cap are unmeasured named constants, and tests assert vetoes and orderings, never their values.
 *
 * <p>It also holds the blocker comparison the export pass, and the one repair of a text that is no translation, read
 * ({@link #isWorseByBlockers}).
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class NeverWorse {

    private static final int WEIGHT_BLOCKING = 5;
    private static final int WEIGHT_SOFT = 1;
    private static final int WEIGHT_AUDIT = 1;
    private static final String SCORE = "score";
    private static final String NO_GAIN = "no-gain";

    /**
     * Whether the candidate may replace the current text.
     *
     * @param current the non-null text in place, evaluated
     * @param candidate the non-null text that would replace it, evaluated the same way
     * @param facts the non-null facts of the window both texts belong to
     * @return {@link Verdict.Keep} if the candidate is kept, or {@link Verdict.Reject} naming the veto or the score
     *     rule that refused it
     */
    public static Verdict judge(final Candidate current, final Candidate candidate, final WindowFacts facts) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(facts, "facts");
        final int before = score(current);
        final int after = score(candidate);
        final Verdict verdict = NeverWorseVetoes.first(current, candidate, facts)
                .<Verdict>map(Verdict.Reject::new)
                .orElseGet(() -> byScore(current, candidate, facts, before, after));
        log.debug(
                "never-worse verdict={} reason={} scoreBefore={} scoreAfter={}",
                verdict instanceof Verdict.Keep ? "keep" : "reject",
                verdict instanceof Verdict.Reject(final String reason) ? reason : "none",
                before,
                after);
        return verdict;
    }

    /**
     * Whether a changed text is worse than the one it would replace by its blockers alone: more failed hard gates, or
     * as many and more blocking checks. Equal is not worse, because a fix the checks cannot see (a gender, a name)
     * leaves them equal.
     *
     * @param candidate the non-null evaluated new text
     * @param before the non-null evaluated text it would replace
     * @return {@code true} if the new text is worse, {@code false} otherwise
     */
    public static boolean isWorseByBlockers(final QaResult candidate, final QaResult before) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(before, "before");
        final long candidateHard = failedHardGates(candidate);
        final long beforeHard = failedHardGates(before);
        return beforeHard < candidateHard
                || (beforeHard == candidateHard
                        && blockers(before).size() < blockers(candidate).size());
    }

    private static Verdict byScore(
            final Candidate current,
            final Candidate candidate,
            final WindowFacts facts,
            final int before,
            final int after) {
        if (after > before) {
            return new Verdict.Reject(SCORE);
        }
        final boolean cleared = facts.triggerFinding()
                .map(trigger -> clears(candidate, trigger))
                .orElse(false);
        final boolean smallRewrite = facts.unmarkedMaxFocus() && isSmallRewrite(current, candidate);
        log.debug(
                "Never-worse score rule cleared={} lower={} unmarkedMax={} smallRewrite={}",
                cleared,
                after < before,
                facts.unmarkedMaxFocus(),
                smallRewrite);
        return cleared || after < before || smallRewrite ? new Verdict.Keep() : new Verdict.Reject(NO_GAIN);
    }

    private static boolean clears(final Candidate candidate, final QaFinding trigger) {
        return candidate.qa().findings().stream()
                .noneMatch(finding -> finding.raisedBy().equals(trigger.raisedBy()));
    }

    private static boolean isSmallRewrite(final Candidate current, final Candidate candidate) {
        final double share =
                TextGuards.replacedShare(DisplayText.of(current.masked()), DisplayText.of(candidate.masked()));
        log.debug("Never-worse rewrite share={} cap={}", share, TextGuards.REWRITE_CAP);
        return share <= TextGuards.REWRITE_CAP;
    }

    // Blocking findings weigh most; a repair or a note weighs one, and so does an audit doubt no finding already names.
    private static int score(final Candidate text) {
        int score = 0;
        for (final QaFinding finding : text.qa().findings()) {
            score += switch (CheckLevels.of(finding)) {
                case BLOCK -> WEIGHT_BLOCKING;
                case REPAIR, NOTE -> WEIGHT_SOFT;
            };
        }
        final Set<String> named =
                text.qa().findings().stream().map(QaFinding::raisedBy).collect(Collectors.toSet());
        final long doubts = text.auditChecks().stream()
                .filter(check -> !named.contains(check))
                .count();
        return score + (int) doubts * WEIGHT_AUDIT;
    }

    private static long failedHardGates(final QaResult qa) {
        return qa.hardGates().stream().filter(result -> !result.passed()).count();
    }

    // The checks that block, named once each: a failed hard gate, or a soft check that failed outright.
    private static Set<String> blockers(final QaResult qa) {
        final Set<String> names = new LinkedHashSet<>();
        qa.hardGates().stream().filter(result -> !result.passed()).forEach(result -> names.add(raiser(result)));
        qa.soft().stream().filter(CheckResult::blocking).forEach(result -> names.add(raiser(result)));
        return names;
    }

    private static String raiser(final CheckResult result) {
        return Optional.ofNullable(result.finding())
                .map(QaFinding::raisedBy)
                .orElse(result.check().raisedBy());
    }
}

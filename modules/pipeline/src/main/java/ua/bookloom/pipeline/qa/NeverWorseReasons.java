package ua.bookloom.pipeline.qa;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.pipeline.DisplayText;
import ua.bookloom.pipeline.checks.LanguageIdentityCheck;

/**
 * A never-worse refusal in words the person can act on: a veto that a word broke names that word — a source-only
 * function word the candidate brought in, a name written in a form the map does not list — after the veto's stable
 * name, so the review desk shows why a proofread rewrite was turned down.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class NeverWorseReasons {

    private static final String RESIDUE = CheckName.FUNCTION_WORD_RESIDUE.raisedBy();

    /**
     * The reason of a refusal.
     *
     * @param veto the non-null name {@link NeverWorse#judge} rejected with
     * @param current the non-null text in place
     * @param candidate the non-null refused text
     * @param facts the non-null facts of the window
     * @return the veto's name, followed by the words that broke it when the veto is one a word breaks; never null
     */
    public static String of(
            final String veto, final Candidate current, final Candidate candidate, final WindowFacts facts) {
        Objects.requireNonNull(veto, "veto");
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(facts, "facts");
        final List<String> words = new ArrayList<>();
        if (veto.equals(RESIDUE) || veto.equals(NeverWorseVetoes.COUNT + RESIDUE)) {
            words.addAll(added(residue(current, facts), residue(candidate, facts)));
        } else if ("name-form".equals(veto)) {
            words.addAll(added(
                    NameVetoes.foreignFormNotes(DisplayText.of(current.masked()), facts),
                    NameVetoes.foreignFormNotes(DisplayText.of(candidate.masked()), facts)));
        }
        final String reason = words.isEmpty() ? veto : veto + ": " + String.join(", ", words);
        log.debug("Never-worse reason veto={} words={}", veto, words.size());
        return reason;
    }

    private static List<String> residue(final Candidate text, final WindowFacts facts) {
        return LanguageIdentityCheck.residueWords(
                DisplayText.of(text.masked()), facts.sourceLanguage(), facts.targetLanguage());
    }

    // What the candidate holds that the current text does not, each once, in the candidate's order.
    private static List<String> added(final List<String> before, final List<String> after) {
        final Set<String> words = new LinkedHashSet<>(after);
        words.removeAll(Set.copyOf(before));
        return List.copyOf(words);
    }
}

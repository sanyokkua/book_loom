package ua.bookloom.pipeline.eval;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import ua.bookloom.api.project.NameKnowledge;
import ua.bookloom.pipeline.eval.BareNames.BareName;

/**
 * Arm 4a's rule over one run's answers (design D24): a model class keeps its likely values in prompts only if at most
 * {@value #WRONG_LIMIT} of its confident answers (confidence {@link NameKnowledge#LOW_CONFIDENCE} or more) about the
 * gendered names give the wrong gender, and every unisex name is answered below that confidence.
 *
 * @param asked the names asked
 * @param answered the names a kept answer came back for
 * @param confident the gendered names answered with confidence 0.7 or more
 * @param confidentWrong of those, the ones whose gender is not the gold gender
 * @param unisex the unisex names answered
 * @param unisexConfident of those, the ones answered with confidence 0.7 or more
 * @param wrongTerms the confidently wrong gendered names
 * @param unisexConfidentTerms the unisex names answered with confidence 0.7 or more
 */
record BareNamesScore(
        int asked,
        int answered,
        int confident,
        int confidentWrong,
        int unisex,
        int unisexConfident,
        List<String> wrongTerms,
        List<String> unisexConfidentTerms) {

    /** The largest share of confident answers that may be wrong. */
    static final double WRONG_LIMIT = 0.05;

    /** Copies the lists. */
    BareNamesScore {
        wrongTerms = List.copyOf(wrongTerms);
        unisexConfidentTerms = List.copyOf(unisexConfidentTerms);
    }

    /**
     * Scores one run.
     *
     * @param names the names asked
     * @param answers the kept answer per term; a term missing here was not answered
     * @return the run's counts
     */
    static BareNamesScore of(final List<BareName> names, final Map<String, NameKnowledge> answers) {
        Objects.requireNonNull(names, "names");
        Objects.requireNonNull(answers, "answers");
        final List<BareName> gendered = names.stream()
                .filter(name -> !name.unisex() && answers.containsKey(name.term()))
                .filter(name -> isConfident(answers.get(name.term())))
                .toList();
        final List<String> wrong = gendered.stream()
                .filter(name -> answerOf(answers, name).gender() != name.gender())
                .map(BareName::term)
                .toList();
        final List<BareName> unisex = names.stream()
                .filter(name -> name.unisex() && answers.containsKey(name.term()))
                .toList();
        final List<String> unisexHigh = unisex.stream()
                .filter(name -> isConfident(answerOf(answers, name)))
                .map(BareName::term)
                .toList();
        final int answered = (int)
                names.stream().filter(name -> answers.containsKey(name.term())).count();
        return new BareNamesScore(
                names.size(),
                answered,
                gendered.size(),
                wrong.size(),
                unisex.size(),
                unisexHigh.size(),
                wrong,
                unisexHigh);
    }

    /** The confidently wrong share; 0 when nothing was answered confidently. */
    double wrongShare() {
        return confident == 0 ? 0 : (double) confidentWrong / confident;
    }

    /** {@code true} if the run meets both halves of the rule, {@code false} otherwise. */
    boolean passes() {
        return wrongShare() <= WRONG_LIMIT && unisexConfident == 0;
    }

    private static boolean isConfident(final NameKnowledge answer) {
        return answer.confidence() >= NameKnowledge.LOW_CONFIDENCE;
    }

    private static NameKnowledge answerOf(final Map<String, NameKnowledge> answers, final BareName name) {
        return Objects.requireNonNull(answers.get(name.term()), name.term());
    }
}

package ua.bookloom.pipeline.lexicon;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * A recurring term never learns its own source word as its rendering across scripts (15i.T4, with 15i.N6's one identity
 * rule): the Latin word «data» left in a Ukrainian sentence is a leftover, not the book's rendering. Both ways a
 * rendering reaches the lexicon are held to it — the model's reported pair and the learner's co-occurrence count.
 * The paired genuine rendering is still learned.
 */
class IdentityRenderingTest {

    private static final List<String> KEY_TERMS = List.of("data");
    private static final String SOURCE = "They lost the data on the way.";
    private static final String KEPT_LATIN = "Вони загубили data дорогою.";
    private static final String TRANSLATED = "Вони загубили дані дорогою.";

    // IF the model's claim "data → data" were counted, THEN every later prompt would show the leftover as the book's
    // established rendering.
    @Test
    void verify_claimOfTheSourceWordAsItsOwnRendering_isDropped() {
        final List<TermMappingVerifier.Pair> pairs = TermMappingVerifier.verify(
                Map.of("data", "data"), KEY_TERMS, SOURCE, KEPT_LATIN, List.of(), "en", "uk");

        assertThat(pairs).isEmpty();
    }

    @Test
    void verify_claimOfAGenuineRendering_isKept() {
        assertThat(TermMappingVerifier.verify(Map.of("data", "дані"), KEY_TERMS, SOURCE, TRANSLATED))
                .containsExactly(new TermMappingVerifier.Pair("data", "дані"));
    }

    @Test
    void established_sourceWordKeptInLatinInEveryTargetSegment_isNotLearnedAsTheRendering() {
        final CooccurrenceLearner learner = new CooccurrenceLearner(List.of(), "en", "uk");
        learner.track("data");
        learner.observe("They lost the data on the way.", KEPT_LATIN);
        learner.observe("The data was old.", "data була старою.");
        learner.observe("Nobody read the data.", "Ніхто не читав data.");

        assertThat(learner.established("data", Set.of())).isEmpty();
    }

    @Test
    void established_genuineRenderingInEveryTargetSegment_isLearned() {
        final CooccurrenceLearner learner = new CooccurrenceLearner(List.of(), "en", "uk");
        learner.track("data");
        learner.observe("They lost the data on the way.", TRANSLATED);
        learner.observe("The data was old.", "Дані були старими.");
        learner.observe("Nobody read the data.", "Ніхто не читав дані.");

        assertThat(learner.established("data", Set.of()))
                .hasValueSatisfying(found -> assertThat(found.rendering()).isEqualTo("дані"));
    }
}

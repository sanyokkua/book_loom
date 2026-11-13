package ua.bookloom.pipeline.lexicon;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The learner counts a recurring term by its stem group: a segment naming any of its forms names the term. */
class CooccurrenceGroupTest {

    @Test
    void established_termWithForms_countsASegmentNamingAnyForm() {
        final CooccurrenceLearner learner = new CooccurrenceLearner();
        learner.track("фонарь", true, List.of("фонаря", "фонарём"));
        learner.observe("Двери были старыми.", "Двері були старі.");
        learner.observe("Колокол звонил.", "Дзвін дзвонив.");

        learner.observe("Он зажёг фонарь.", "Він запалив ліхтар.");
        learner.observe("Свет фонаря дрожал.", "Світло ліхтаря тремтіло.");
        learner.observe("Она шла с фонарём.", "Вона йшла з ліхтарем.");

        assertThat(learner.established("фонарь", List.of()))
                .hasValueSatisfying(found -> assertThat(found.occurrences()).isEqualTo(3));
    }

    @Test
    void established_termWithoutItsForms_missesTheInflectedSegments() {
        final CooccurrenceLearner learner = new CooccurrenceLearner();
        learner.track("фонарь", true);

        learner.observe("Он зажёг фонарь.", "Він запалив ліхтар.");
        learner.observe("Свет фонаря дрожал.", "Світло ліхтаря тремтіло.");
        learner.observe("Она шла с фонарём.", "Вона йшла з ліхтарем.");

        assertThat(learner.established("фонарь", List.of())).isEmpty();
    }
}

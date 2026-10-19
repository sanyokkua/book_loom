package ua.bookloom.pipeline.lexicon;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Which surface form of a learned stem becomes the rendering the prompts carry. */
class CooccurrenceBaseFormTest {

    private static final Set<String> NO_NAMES = Set.of();
    private static final int CHATTER = 24;

    private static final List<String[]> FILLER = List.of(
            new String[] {"The harbour was quiet.", "Гавань була тиха, і він мовчав."},
            new String[] {"It rained all night.", "Усю ніч йшов дощ, і що він міг зробити?"},
            new String[] {"Boats rested at the quay.", "Човни стояли біля причалу, і він дивився на них."},
            new String[] {"Nobody spoke.", "Ніхто не сказав ні слова, що було дивно."},
            new String[] {"The door was old.", "Двері були старі, і він це знав."},
            new String[] {"A bell rang twice.", "Двічі продзвенів дзвін, і що тепер?"});

    private static CooccurrenceLearner learnerWithFiller(final String... terms) {
        final CooccurrenceLearner learner = new CooccurrenceLearner();
        for (final String term : terms) {
            learner.track(term);
        }
        FILLER.forEach(pair -> learner.observe(pair[0], pair[1]));
        for (int i = 0; i < CHATTER; i++) {
            learner.observe("He knew it.", "І він знав, що це так.");
        }
        return learner;
    }

    private static java.util.Optional<CooccurrenceLearner.Learned> established(
            final CooccurrenceLearner learner, final String term) {
        return learner.established(term, NO_NAMES);
    }

    // IF the base form were the cut-off the other forms of обличчя extend, THEN every prompt would carry "облич".
    @Test
    void established_formThatOnlyLongerFormsExtendByTwoLetters_isNotTheRendering() {
        final CooccurrenceLearner learner = learnerWithFiller("face");
        learner.observe("Many a face smiled.", "Багато облич усміхалося.");
        learner.observe("No face was seen.", "Жодного облич не було видно.");
        learner.observe("Her face was pale.", "Її облич було бліде.");
        learner.observe("The face grew pale.", "Обличчя зблідло.");
        learner.observe("A face in the dark.", "Обличчя в темряві.");
        learner.observe("She hid her face.", "Вона сховала обличчям.");

        assertThat(established(learner, "face"))
                .hasValueSatisfying(found -> assertThat(found.rendering()).isEqualTo("обличчя"));
    }

    // Among forms no other form extends, the shorter is nearer the dictionary form, even if the longer is used more.
    @Test
    void established_commonFormsNothingExtends_prefersTheShorterOne() {
        final CooccurrenceLearner learner = learnerWithFiller("console");
        learner.observe("Two consoles hummed.", "Кілька консолей гуділо.");
        learner.observe("The consoles blinked.", "Багато консолей блимало.");
        learner.observe("Old consoles failed.", "Серед консолей щось трісло.");
        learner.observe("The console glowed.", "Консоль світилася.");
        learner.observe("A console beeped.", "Консоль пищала.");

        assertThat(established(learner, "console"))
                .hasValueSatisfying(found -> assertThat(found.rendering()).isEqualTo("консоль"));
    }
}

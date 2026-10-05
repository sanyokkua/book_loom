package ua.bookloom.pipeline.lexicon;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ua.bookloom.pipeline.lexicon.CooccurrenceLearner.Learned;

/**
 * Synthetic English to Ukrainian pairs only. The learner reads decided pairs and names the target word that keeps
 * company with a term; where the evidence is split or thin it says nothing, because a wrong rendering injected into
 * every later prompt is worse than none.
 */
class CooccurrenceLearnerTest {

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

    private static Optional<Learned> established(final CooccurrenceLearner learner, final String term) {
        return learner.established(term, NO_NAMES);
    }

    @Test
    void established_clearNounAtTheThirdOccurrence_isLearnedAndNotBefore() {
        final CooccurrenceLearner learner = learnerWithFiller("master");

        learner.observe("The master walked to the harbour.", "Господар пішов до гавані.");
        learner.observe("The master opened the door.", "Господар відчинив двері.");
        assertThat(established(learner, "master")).isEmpty();

        learner.observe("The master smiled at the sea.", "Господар посміхнувся морю.");

        assertThat(established(learner, "master"))
                .hasValueSatisfying(found -> assertThat(found).isEqualTo(new Learned("господар", 3, 3, 1.0)));
    }

    @Test
    void established_commonWordsInEveryTermSegment_neverWin() {
        final CooccurrenceLearner learner = learnerWithFiller("boy");

        learner.observe("The boy ran, and he fell.", "І хлопець побіг, і він упав, що було боляче.");
        learner.observe("The boy hid, and he waited.", "Хлопець сховався, і він чекав, що буде далі.");
        learner.observe("A boy cried, and he left.", "Заплакав хлопець, і він пішов, що тут скажеш.");

        assertThat(established(learner, "boy"))
                .hasValueSatisfying(found -> assertThat(found.rendering()).isEqualTo("хлопець"));
    }

    @Test
    void established_capitalisedMidSentenceWord_isAName_andIsIgnored() {
        final CooccurrenceLearner learner = learnerWithFiller("djinni");

        learner.observe("The djinni laughed.", "Тоді засміявся Бартімеус голосно.");
        learner.observe("The djinni sulked.", "Там похмурився Бартімеус довго.");
        learner.observe("The djinni waited.", "Тут чекав Бартімеус мовчки.");

        assertThat(established(learner, "djinni")).isEmpty();
    }

    @Test
    void established_wordTheGlossaryHolds_isNotARendering() {
        final CooccurrenceLearner learner = learnerWithFiller("djinni");
        learner.observe("The djinni laughed.", "Засміявся джин голосно.");
        learner.observe("The djinni sulked.", "Похмурився джин довго.");
        learner.observe("The djinni waited.", "Чекав джин мовчки.");

        assertThat(established(learner, "djinni")).isPresent();
        assertThat(learner.established("djinni", Set.of("джин"))).isEmpty();
    }

    @Test
    void established_inflectedForms_mergeIntoOneStemAndTheMostUsedFormIsTheBase() {
        final CooccurrenceLearner learner = learnerWithFiller("master");

        learner.observe("The master came.", "Господар прийшов.");
        learner.observe("He bowed to the master.", "Він вклонився господарю.");
        learner.observe("The master's boat sank.", "Човен господаря затонув.");
        learner.observe("The master left.", "Господар пішов.");

        assertThat(established(learner, "master"))
                .hasValueSatisfying(found -> assertThat(found)
                        .extracting(Learned::rendering, Learned::support, Learned::occurrences)
                        .containsExactly("господар", 4, 4));
    }

    @Test
    void established_twoRenderingsInANearSplit_isNothing() {
        final CooccurrenceLearner learner = learnerWithFiller("master");

        learner.observe("The master came.", "Господар прийшов.");
        learner.observe("The master left.", "Учитель пішов.");
        learner.observe("The master sat.", "Господар сів.");
        learner.observe("The master stood.", "Учитель встав.");
        learner.observe("The master ate.", "Господар їв.");
        learner.observe("The master slept.", "Учитель спав.");

        assertThat(established(learner, "master")).isEmpty();
    }

    @Test
    void established_titleBeforeChangingSurnames_isNothing() {
        final CooccurrenceLearner learner = learnerWithFiller("Mr");

        learner.observe("Mr Button came.", "Містер Баттон прийшов.");
        learner.observe("He saw Mr Hopkins.", "Він бачив пана Гопкінса.");
        learner.observe("Mr Underwood left.", "Містер Андервуд пішов.");
        learner.observe("Mr Simpkins sat.", "Пан Сімпкінс сів.");

        assertThat(established(learner, "Mr")).isEmpty();
    }

    @Test
    void established_wordWithTwoMeanings_isNothing() {
        final CooccurrenceLearner learner = learnerWithFiller("staff");

        learner.observe("He gripped the staff.", "Він стиснув посох.");
        learner.observe("The staff glowed.", "Посох засяяв.");
        learner.observe("A staff of ash.", "Посох із ясена.");
        learner.observe("The staff arrived.", "Персонал прибув.");
        learner.observe("The staff waited.", "Персонал чекав.");
        learner.observe("The staff left.", "Персонал пішов.");
        learner.observe("The staff slept.", "Персонал спав.");

        assertThat(established(learner, "staff")).isEmpty();
    }

    @Test
    void established_termNobodyTracks_isNothing() {
        final CooccurrenceLearner learner = learnerWithFiller();
        for (int i = 0; i < 4; i++) {
            learner.observe("The master came " + i + ".", "Господар прийшов " + i + ".");
        }

        assertThat(established(learner, "master")).isEmpty();
    }

    @Test
    void established_sameInputsTwice_giveTheSameAnswer() {
        final CooccurrenceLearner first = learnerWithFiller("master", "boy");
        final CooccurrenceLearner second = learnerWithFiller("master", "boy");
        for (final CooccurrenceLearner learner : List.of(first, second)) {
            learner.observe("The master came.", "Господар прийшов.");
            learner.observe("The master left.", "Господар пішов.");
            learner.observe("The master sat.", "Господар сів.");
        }

        assertThat(established(first, "master")).isEqualTo(established(second, "master"));
        assertThat(established(first, "master")).isPresent();
    }

    @Test
    void observe_sourceTermAsPluralOrPossessive_countsAsAnOccurrence() {
        final CooccurrenceLearner learner = learnerWithFiller("master");

        learner.observe("The masters came.", "Господарі прийшли.");
        learner.observe("The master's hat.", "Капелюх господаря.");
        learner.observe("The master left.", "Господар пішов.");

        assertThat(established(learner, "master")).isPresent();
    }

    @Test
    void established_termMostlyMetInAnObliqueCase_stillGivesTheFormTheOthersExtend() {
        final CooccurrenceLearner learner = learnerWithFiller("master");

        learner.observe("He bowed to the master.", "Він вклонився господарю.");
        learner.observe("The master's boat sank.", "Човен господаря затонув.");
        learner.observe("He thanked the master.", "Він подякував господарю.");
        learner.observe("The master left.", "Господар пішов.");

        assertThat(established(learner, "master"))
                .hasValueSatisfying(found -> assertThat(found.rendering()).isEqualTo("господар"));
    }
}

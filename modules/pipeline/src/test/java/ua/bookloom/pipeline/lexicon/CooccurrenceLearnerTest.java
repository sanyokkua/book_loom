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

    // The run learned mr -> пані because Mr matched the segments of Mrs through the plural ending.
    @Test
    void established_mrAndMrsTrackedTogether_eachKeepsItsOwnRendering() {
        final CooccurrenceLearner learner = learnerWithFiller("Mr", "Mrs");

        learner.observe("Mr Hale left.", "Пан Хейл пішов.");
        learner.observe("Mr Hale slept.", "Пан Хейл спав.");
        learner.observe("Mr Hale ate.", "Пан Хейл їв.");
        learner.observe("Mrs Hale came.", "Пані Хейл прийшла.");
        learner.observe("Mrs Hale sat.", "Пані Хейл сіла.");
        learner.observe("Mrs Hale sang.", "Пані Хейл співала.");
        learner.observe("Mrs Hale wept.", "Пані Хейл плакала.");

        assertThat(established(learner, "Mr"))
                .hasValueSatisfying(found -> assertThat(found.rendering()).isEqualTo("пан"));
        assertThat(established(learner, "Mrs"))
                .hasValueSatisfying(found -> assertThat(found.rendering()).isEqualTo("пані"));
    }

    @Test
    void established_segmentWithBothTitles_countsForBoth() {
        final CooccurrenceLearner learner = learnerWithFiller("Mr", "Mrs");

        learner.observe("Mr Hale and Mrs Hale left.", "Пан Хейл і пані Хейл пішли.");
        learner.observe("Mr Hale and Mrs Hale sat.", "Пан Хейл і пані Хейл сіли.");
        learner.observe("Mr Hale and Mrs Hale ate.", "Пан Хейл і пані Хейл їли.");

        assertThat(learner.established("Mr", NO_NAMES)).isPresent();
        assertThat(learner.established("Mrs", NO_NAMES)).isPresent();
    }

    // Among землі, земля and землю the dictionary form is the one with no oblique ending.
    @Test
    void established_withObliqueEndingData_prefersTheDictionaryForm() {
        final CooccurrenceLearner learner = new CooccurrenceLearner(List.of("і", "ю", "и", "ів"));
        learner.track("earth");
        FILLER.forEach(pair -> learner.observe(pair[0], pair[1]));
        learner.observe("The earth shook.", "Землі трясло.");
        learner.observe("He knelt on the earth.", "Він став навколішки на землю.");
        learner.observe("The earth was cold.", "Земля була холодна.");
        learner.observe("Under the earth it was dark.", "Під землі було темно.");
        learner.observe("The earth slept.", "Землі спалося.");

        assertThat(learner.established("earth", NO_NAMES))
                .hasValueSatisfying(found -> assertThat(found.rendering()).isEqualTo("земля"));
    }

    @Test
    void established_withoutLanguageData_keepsTheMostExtendedForm() {
        final CooccurrenceLearner learner = learnerWithFiller("earth");
        learner.observe("The earth shook.", "Землі трясло.");
        learner.observe("The earth was cold.", "Земля була холодна.");
        learner.observe("Under the earth it was dark.", "Під землі було темно.");
        learner.observe("The earth slept.", "Землі спалося.");

        assertThat(established(learner, "earth"))
                .hasValueSatisfying(found -> assertThat(found.rendering()).isEqualTo("землі"));
    }

    // The newspaper The Times is a name: its words are never a common word's rendering.
    @Test
    void established_titleCaseNameOfACommonWord_learnsNothing() {
        final CooccurrenceLearner learner = new CooccurrenceLearner();
        learner.track("times", true);
        FILLER.forEach(pair -> learner.observe(pair[0], pair[1]));
        learner.observe("He read The Times.", "Він читав часи.");
        learner.observe("She quoted The Times.", "Вона цитувала часи.");
        learner.observe("The Times printed it.", "Часи надрукували це.");
        learner.observe("The Times wrote so.", "Часи написали так.");

        assertThat(learner.established("times", NO_NAMES)).isEmpty();
    }

    @Test
    void established_commonWordAlsoWrittenInLowerCase_isStillLearned() {
        final CooccurrenceLearner learner = new CooccurrenceLearner();
        learner.track("times", true);
        FILLER.forEach(pair -> learner.observe(pair[0], pair[1]));
        learner.observe("It was hard in those times.", "Було важко в ті часи.");
        learner.observe("Times were hard.", "Часи були важкі.");
        learner.observe("In bad times we wept.", "У лихі часи ми плакали.");
        learner.observe("The times changed.", "Часи змінилися.");

        assertThat(learner.established("times", NO_NAMES)).isPresent();
    }

    private static void observeMaster(final CooccurrenceLearner learner, final String rendering, final int times) {
        final List<String> verbs = List.of("прийшов", "пішов", "сів", "встав", "їв", "спав", "курив", "читав");
        for (int i = 0; i < times; i++) {
            learner.observe("The master " + i + " did it.", rendering + " " + verbs.get(i % verbs.size()) + ".");
        }
    }

    @Test
    void retained_renderingTheRivalCaughtUp_isKeptWhileTheGateSaysNothing() {
        final CooccurrenceLearner learner = learnerWithFiller("master");
        observeMaster(learner, "Господар", 3);
        assertThat(established(learner, "master")).isPresent();
        observeMaster(learner, "Учитель", 3);

        assertThat(established(learner, "master")).isEmpty();
        assertThat(learner.retained("master", "господар", NO_NAMES))
                .hasValueSatisfying(found -> assertThat(found)
                        .extracting(Learned::rendering, Learned::support, Learned::occurrences)
                        .containsExactly("господар", 3, 6));
    }

    @Test
    void retained_renderingTheRivalClearlyOvertook_isDropped() {
        final CooccurrenceLearner learner = learnerWithFiller("master");
        observeMaster(learner, "Господар", 3);
        observeMaster(learner, "Учитель", 8);

        assertThat(learner.retained("master", "господар", NO_NAMES)).isEmpty();
    }

    @Test
    void retained_renderingTheBookLeftBehind_isDropped() {
        final CooccurrenceLearner learner = learnerWithFiller("master");
        observeMaster(learner, "Господар", 3);
        observeMaster(learner, "Хтось", 30);

        assertThat(learner.retained("master", "господар", NO_NAMES)).isEmpty();
    }

    @Test
    void retained_termNobodyTracks_isNothing() {
        assertThat(new CooccurrenceLearner().retained("master", "господар", NO_NAMES))
                .isEmpty();
    }

    @Test
    void established_nameWithTwoSpellingsOfOneStem_isOneRenderingInTheMostUsedSpelling() {
        final CooccurrenceLearner learner = learnerWithFiller();
        learner.trackName("Bartimaeus");

        learner.observe("Bartimaeus laughed.", "Бартімей засміявся.");
        learner.observe("Then Bartimaeus left.", "Потім пішов Бартимей.");
        learner.observe("Bartimaeus sat down.", "Бартімей сів.");
        learner.observe("They saw Bartimaeus.", "Вони побачили Бартімея.");

        assertThat(established(learner, "Bartimaeus"))
                .hasValueSatisfying(found -> assertThat(found)
                        .extracting(Learned::rendering, Learned::support, Learned::occurrences)
                        .containsExactly("Бартімей", 4, 4));
    }

    @Test
    void established_nameWithOnlyACommonSentenceStartWord_isNothing() {
        final CooccurrenceLearner learner = learnerWithFiller();
        learner.trackName("Bartimaeus");

        learner.observe("Bartimaeus laughed.", "Він засміявся голосно.");
        learner.observe("Bartimaeus sat.", "Він сидів голосно.");
        learner.observe("Bartimaeus left.", "Він пішов голосно.");

        assertThat(established(learner, "Bartimaeus")).isEmpty();
    }

    @Test
    void established_titleWrittenBesideEverySurname_isNotTheSpellingOfOneName() {
        final CooccurrenceLearner learner = learnerWithFiller();
        learner.trackName("Lovelace");

        learner.observe("Ms Lovelace laughed.", "Міс Лавлейс засміялась.");
        learner.observe("Ms Lovelace sat.", "Міс Лавлейс сиділа.");
        learner.observe("Ms Lovelace left.", "Міс Лавлейс пішла.");
        learner.observe("Ms Whitlock came.", "Міс Вітлок прийшла.");
        learner.observe("Ms Quill spoke.", "Міс Квіл мовила.");
        learner.observe("Ms Harrow wrote.", "Міс Гарроу писала.");

        assertThat(established(learner, "Lovelace"))
                .hasValueSatisfying(found -> assertThat(found.rendering()).isEqualTo("Лавлейс"));
    }

    @Test
    void established_titleWrittenWithACapitalMidSentence_isLearnedForATitleTrackedAsOne() {
        final CooccurrenceLearner learner = learnerWithFiller();
        learner.trackTitle("Ms");

        learner.observe("Then Ms Hale spoke.", "Тоді Міс Гейл промовила.");
        learner.observe("Ms Moore left.", "Потім Міс Мур пішла.");
        learner.observe("They saw Ms West.", "Вони бачили Міс Вест.");

        assertThat(established(learner, "Ms"))
                .hasValueSatisfying(found -> assertThat(found.rendering()).isEqualTo("міс"));
    }

    @Test
    void established_sameTitleTrackedAsACommonTerm_ignoresTheCapitalisedWord() {
        final CooccurrenceLearner learner = learnerWithFiller();
        learner.track("Ms", false);

        learner.observe("Then Ms Hale spoke.", "Тоді Міс Гейл промовила.");
        learner.observe("Ms Moore left.", "Потім Міс Мур пішла.");
        learner.observe("They saw Ms West.", "Вони бачили Міс Вест.");

        assertThat(established(learner, "Ms")).isEmpty();
    }
}

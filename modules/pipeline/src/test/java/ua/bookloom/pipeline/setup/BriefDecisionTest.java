package ua.bookloom.pipeline.setup;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.pipeline.BriefField;
import ua.bookloom.api.pipeline.FieldEvidence.Basis;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.NarratorHint;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.Register;
import ua.bookloom.pipeline.setup.BriefDecision.Decided;
import ua.bookloom.pipeline.setup.BriefReplies.Answer;

/**
 * One decision per brief field over the windows' verified answers (15i.B4), with hard-coded tables: the register and
 * the narrator by a strict majority of the windows read, the narrator by the detector first, the narrator's gender by
 * verified votes that do not contradict each other, and genre, audience and voice by the synthesis call, kept only on
 * the basis the code can check (accepted knowledge that agrees, two windows with a reading, or one window with a quote
 * when knowledge agrees or when the whole story is one window). An answer a window could not prove is no vote: it never
 * reaches this table (it is dropped by {@link BriefReplies}), so it appears here as a window with no answer. The
 * synthesis fields are proven in {@link BriefDecisionSynthesisTest}.
 */
class BriefDecisionTest {

    private static final String Q1 = "The lamps were lit early that winter";
    private static final String Q2 = "Nobody in the lane spoke of the mill";
    private static final String Q3 = "She counted the coins twice before bed";
    private static final String Q4 = "The ferry left without its bell";

    private static Map<BriefField, Answer> says(final BriefField field, final String value, final String quote) {
        final Map<BriefField, Answer> window = new EnumMap<>(BriefField.class);
        window.put(field, new Answer(value, quote));
        return window;
    }

    private static Map<BriefField, Answer> nothing() {
        return new EnumMap<>(BriefField.class);
    }

    @SafeVarargs
    private static List<@Nullable Map<BriefField, Answer>> windows(final @Nullable Map<BriefField, Answer>... read) {
        return new ArrayList<>(Arrays.asList(read));
    }

    // --- register: a strict majority of the windows read ---

    @Test
    void register_threeOfFourWindowsSayCasual_isCasualFromThreeOfFourPassages() {
        final Decided<Register> decided = BriefDecision.register(windows(
                says(BriefField.REGISTER, "casual", Q1),
                says(BriefField.REGISTER, "casual", Q2),
                says(BriefField.REGISTER, "casual", Q3),
                says(BriefField.REGISTER, "formal", Q4)));

        assertThat(decided.value()).isEqualTo(Register.CASUAL);
        assertThat(decided.evidence().basis()).isEqualTo(Basis.PASSAGES);
        assertThat(decided.evidence().quotes()).containsExactly(Q1, Q2, Q3);
        assertThat(decided.evidence().agreeing()).isEqualTo(3);
        assertThat(decided.evidence().samples()).isEqualTo(4);
        assertThat(decided.evidence().describe()).isEqualTo("from 3 of 4 passages");
    }

    // IF half the windows were a majority, THEN two windows of a mixed book could set its register for every call.
    @Test
    void register_twoOfFourSayCasualAndTwoFormal_isNeutralAndUncertain() {
        final Decided<Register> decided = BriefDecision.register(windows(
                says(BriefField.REGISTER, "casual", Q1),
                says(BriefField.REGISTER, "formal", Q2),
                says(BriefField.REGISTER, "casual", Q3),
                says(BriefField.REGISTER, "formal", Q4)));

        assertThat(decided.value()).isEqualTo(Register.NEUTRAL);
        assertThat(decided.evidence().isKept()).isFalse();
        assertThat(decided.evidence().agreeing()).isEqualTo(2);
        assertThat(decided.evidence().samples()).isEqualTo(4);
    }

    // IF the windows that proved nothing were left out of the count, THEN two verified answers of four would be "2 of
    // 2" and decide the field.
    @Test
    void register_twoVerifiedAnswersAndTwoWindowsWithNone_isNeutralAndUncertain() {
        final Decided<Register> decided = BriefDecision.register(windows(
                says(BriefField.REGISTER, "casual", Q1), says(BriefField.REGISTER, "casual", Q2), nothing(), null));

        assertThat(decided.value()).isEqualTo(Register.NEUTRAL);
        assertThat(decided.evidence().isKept()).isFalse();
        assertThat(decided.evidence().samples()).isEqualTo(4);
    }

    // A neutral register needs no quote: three windows saying so keep it, as a decision and not as a doubt.
    @Test
    void register_threeOfFourSayNeutral_isNeutralAndKept() {
        final Decided<Register> decided = BriefDecision.register(windows(
                says(BriefField.REGISTER, "neutral", ""),
                says(BriefField.REGISTER, "neutral", ""),
                says(BriefField.REGISTER, "neutral", ""),
                says(BriefField.REGISTER, "formal", Q4)));

        assertThat(decided.value()).isEqualTo(Register.NEUTRAL);
        assertThat(decided.evidence().isKept()).isTrue();
        assertThat(decided.evidence().quotes()).isEmpty();
    }

    // --- narrator person: the detector when it answers, else a strict majority ---

    // IF the windows outvoted the detector, THEN a letter quoted inside a third-person novel would make it first
    // person: the detector counted every chapter, the windows read a few passages.
    // A mixed book is first person when at least a quarter of its judged chapters are: alternating narrators and a book
    // with a substantial first-person share need the narrator rules, while a third-person novel with a first-person
    // preface or a character's letters stays third.
    @ParameterizedTest
    @CsvSource({
        "FIRST, 1, 1, FIRST",
        "MIXED, 2, 3, FIRST",
        "MIXED, 2, 4, FIRST",
        "MIXED, 1, 3, FIRST",
        "MIXED, 1, 4, FIRST",
        "MIXED, 1, 5, THIRD",
        "MIXED, 5, 24, THIRD",
        "MIXED, 6, 24, FIRST",
        "MIXED, 1, 40, THIRD",
        "THIRD, 0, 4, THIRD"
    })
    void narrator_detectorAnswers_isTheDetectorsPersonWhateverTheWindowsSay(
            final NarratorHint.Person detected,
            final int firstPersonChapters,
            final int judged,
            final NarratorPerson expected) {
        final List<@Nullable Map<BriefField, Answer>> windows = windows(
                says(BriefField.NARRATOR, "unspecified", ""),
                says(BriefField.NARRATOR, "unspecified", ""),
                says(BriefField.NARRATOR, "unspecified", ""),
                says(BriefField.NARRATOR, "unspecified", ""));

        final Decided<NarratorPerson> decided = BriefDecision.narrator(
                new NarratorHint(
                        detected,
                        0.5,
                        IntStream.rangeClosed(1, firstPersonChapters).boxed().toList(),
                        judged),
                windows);

        assertThat(decided.value()).isEqualTo(expected);
        assertThat(decided.evidence().basis()).isEqualTo(Basis.MEASURED);
    }

    @Test
    void narrator_detectorSaysThirdAndEveryWindowSaysFirst_isThird() {
        final Decided<NarratorPerson> decided = BriefDecision.narrator(
                new NarratorHint(NarratorHint.Person.THIRD, 0.0, List.of(), 4),
                windows(
                        says(BriefField.NARRATOR, "first", Q1),
                        says(BriefField.NARRATOR, "first", Q2),
                        says(BriefField.NARRATOR, "first", Q3),
                        says(BriefField.NARRATOR, "first", Q4)));

        assertThat(decided.value()).isEqualTo(NarratorPerson.THIRD);
        assertThat(decided.evidence().isKept()).isTrue();
    }

    @Test
    void narrator_noDetectorAndThreeOfFourWindowsSayFirst_isFirstFromThreeOfFourPassages() {
        final Decided<NarratorPerson> decided = BriefDecision.narrator(
                null,
                windows(
                        says(BriefField.NARRATOR, "first", Q1),
                        says(BriefField.NARRATOR, "first", Q2),
                        says(BriefField.NARRATOR, "third", Q3),
                        says(BriefField.NARRATOR, "first", Q4)));

        assertThat(decided.value()).isEqualTo(NarratorPerson.FIRST);
        assertThat(decided.evidence().describe()).isEqualTo("from 3 of 4 passages");
    }

    @Test
    void narrator_noDetectorAndTwoOfFour_isUnspecifiedAndUncertain() {
        final Decided<NarratorPerson> decided = BriefDecision.narrator(
                null,
                windows(
                        says(BriefField.NARRATOR, "first", Q1),
                        says(BriefField.NARRATOR, "third", Q2),
                        says(BriefField.NARRATOR, "first", Q3),
                        nothing()));

        assertThat(decided.value()).isEqualTo(NarratorPerson.UNSPECIFIED);
        assertThat(decided.evidence().isKept()).isFalse();
    }

    // --- narrator gender: verified votes with no contradiction ---

    @Test
    void narratorGender_twoVerifiedFemaleVotes_isFemaleWithBothQuotes() {
        final Decided<Gender> decided = BriefDecision.narratorGender(
                NarratorPerson.FIRST,
                windows(
                        says(BriefField.NARRATOR_GENDER, "female", "Thank you, ma'am"),
                        says(BriefField.NARRATOR_GENDER, "unknown", ""),
                        says(BriefField.NARRATOR_GENDER, "female", "Mind the gate, miss"),
                        nothing()));

        assertThat(decided.value()).isEqualTo(Gender.FEMALE);
        assertThat(decided.evidence().isKept()).isTrue();
        assertThat(decided.evidence().quotes()).containsExactly("Thank you, ma'am", "Mind the gate, miss");
        assertThat(decided.evidence().describe()).isEqualTo("from 2 of 4 passages");
    }

    // A vocative the code verified is evidence by itself; it needs no second window to agree, only none to disagree.
    @Test
    void narratorGender_oneVerifiedVoteAndNoOther_isThatGender() {
        final Decided<Gender> decided = BriefDecision.narratorGender(
                NarratorPerson.FIRST,
                windows(
                        says(BriefField.NARRATOR_GENDER, "unknown", ""),
                        says(BriefField.NARRATOR_GENDER, "male", "Hold on tight, Henry"),
                        says(BriefField.NARRATOR_GENDER, "unknown", ""),
                        says(BriefField.NARRATOR_GENDER, "unknown", "")));

        assertThat(decided.value()).isEqualTo(Gender.MALE);
        assertThat(decided.evidence().quotes()).containsExactly("Hold on tight, Henry");
    }

    // IF one verified vote outweighed another, THEN a man's name said to someone else would decide a woman narrator.
    @Test
    void narratorGender_verifiedVotesContradict_isUnknownAndUncertain() {
        final Decided<Gender> decided = BriefDecision.narratorGender(
                NarratorPerson.FIRST,
                windows(
                        says(BriefField.NARRATOR_GENDER, "female", "Thank you, ma'am"),
                        says(BriefField.NARRATOR_GENDER, "female", "Mind the gate, miss"),
                        says(BriefField.NARRATOR_GENDER, "male", "Hold on tight, Henry"),
                        nothing()));

        assertThat(decided.value()).isEqualTo(Gender.UNKNOWN);
        assertThat(decided.evidence().isKept()).isFalse();
    }

    @Test
    void narratorGender_thirdPersonNarrator_isUnknownWhateverTheVotes() {
        final Decided<Gender> decided = BriefDecision.narratorGender(
                NarratorPerson.THIRD,
                windows(
                        says(BriefField.NARRATOR_GENDER, "female", "Thank you, ma'am"),
                        says(BriefField.NARRATOR_GENDER, "female", "Mind the gate, miss")));

        assertThat(decided.value()).isEqualTo(Gender.UNKNOWN);
    }

    // --- the source language under Auto: a proposal from the windows ---

    @Test
    void languageProposal_threeOfFourWindowsSayFrench_isFrench() {
        assertThat(BriefDecision.languageProposal(Arrays.asList("fr", "fr", "en", "fr")))
                .isEqualTo("fr");
    }

    @Test
    void languageProposal_twoAndTwo_isNone() {
        assertThat(BriefDecision.languageProposal(Arrays.asList("fr", "en", "fr", "en")))
                .isNull();
    }

    @Test
    void languageProposal_noWindowNamedALanguage_isNone() {
        assertThat(BriefDecision.languageProposal(Arrays.asList(null, null, null, null)))
                .isNull();
    }
}

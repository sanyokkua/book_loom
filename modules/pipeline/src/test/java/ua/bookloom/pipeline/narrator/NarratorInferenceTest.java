package ua.bookloom.pipeline.narrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.project.Gender;
import ua.bookloom.pipeline.narrator.NarratorInference.Verdict;

/**
 * An unstated first-person narrator's gender from the decided targets (15i.T5, 15f.A4): after 20 first-person segments,
 * eight or more «я + past verb» forms outside quotes that agree at 80 % set it; the others are the minority.
 */
class NarratorInferenceTest {

    private static final String UK = "uk";
    private static final String NEUTRAL = "Надворі було тихо й холодно.";
    private static final List<String> MASCULINE = List.of(
            "Я пішов додому крізь холодний дощ.",
            "Я сказав старому клерку правду.",
            "Я побачив світло у вікні.",
            "Я відчинив важкі двері.",
            "Я взяв ключ зі столу.",
            "Я не бачив нікого на вулиці.",
            "Я знав цю дорогу дуже добре.",
            "Я написав листа тієї ночі.",
            "Я почув кроки на сходах.",
            "Я чекав цілу годину.");
    private static final List<String> FEMININE = List.of(
            "Я пішла додому крізь холодний дощ.",
            "Я сказала старому клерку правду.",
            "Я побачила світло у вікні.",
            "Я відчинила важкі двері.",
            "Я взяла ключ зі столу.",
            "Я не бачила нікого на вулиці.",
            "Я знала цю дорогу дуже добре.",
            "Я написала листа тієї ночі.",
            "Я почула кроки на сходах.",
            "Я чекала цілу годину.");

    // Twenty segments: the given ones first, then neutral narration, which holds no form at all.
    private static List<String> twenty(final List<String> segments) {
        final List<String> all = new ArrayList<>(segments);
        IntStream.range(segments.size(), NarratorInference.MIN_SEGMENTS).forEach(index -> all.add(NEUTRAL));
        return all;
    }

    private static List<String> take(final List<String> pool, final int count) {
        return pool.subList(0, count);
    }

    private static List<String> join(final List<String> first, final List<String> second) {
        final List<String> both = new ArrayList<>(first);
        both.addAll(second);
        return both;
    }

    // IF the majority were not counted, THEN a male narrator's two feminine slips would leave the gender unknown and
    // nothing would re-render them.
    @Test
    void infer_tenMasculineAndTwoFeminineForms_isMaleAndNamesTheTwoSlips() {
        final List<String> targets = new ArrayList<>(twenty(join(take(MASCULINE, 10), take(FEMININE, 2))));
        Collections.swap(targets, 3, 10);
        Collections.swap(targets, 8, 11);

        final Verdict verdict = NarratorInference.infer(targets, UK).orElseThrow();

        assertThat(verdict.gender()).isEqualTo(Gender.MALE);
        assertThat(verdict.forms()).isEqualTo(12);
        assertThat(verdict.agreeing()).isEqualTo(10);
        assertThat(verdict.minority()).containsExactly(3, 8);
    }

    @Test
    void infer_nineFeminineAndOneMasculineForm_isFemaleAndNamesTheSlip() {
        final List<String> targets = twenty(join(take(FEMININE, 9), List.of(MASCULINE.get(9))));

        final Verdict verdict = NarratorInference.infer(targets, UK).orElseThrow();

        assertThat(verdict.gender()).isEqualTo(Gender.FEMALE);
        assertThat(verdict.minority()).containsExactly(9);
    }

    // IF 70 % passed, THEN a woman's book with three masculine slips in ten forms could end with a male narrator.
    @Test
    void infer_sevenFeminineAndThreeMasculineForms_decidesNothing() {
        final List<String> targets = twenty(join(take(FEMININE, 7), take(MASCULINE, 3)));

        assertThat(NarratorInference.infer(targets, UK)).isEmpty();
    }

    // The bar is "at 80 %", inclusive: eight of ten agree.
    @Test
    void infer_eightOfTenFormsAgree_isDecided() {
        final List<String> targets = twenty(join(take(MASCULINE, 8), take(FEMININE, 2)));

        assertThat(NarratorInference.infer(targets, UK))
                .hasValueSatisfying(verdict -> assertThat(verdict.gender()).isEqualTo(Gender.MALE));
    }

    @Test
    void infer_eightFormsAllAgreeing_isDecided() {
        assertThat(NarratorInference.infer(twenty(take(MASCULINE, 8)), UK))
                .hasValueSatisfying(verdict -> assertThat(verdict.forms()).isEqualTo(8));
    }

    @Test
    void infer_sevenFormsAllAgreeing_isTooFewToDecide() {
        assertThat(NarratorInference.infer(twenty(take(MASCULINE, 7)), UK)).isEmpty();
    }

    @Test
    void infer_nineteenFirstPersonSegments_decidesNothingHoweverClear() {
        final List<String> nineteen = take(twenty(take(MASCULINE, 10)), NarratorInference.MIN_SEGMENTS - 1);

        assertThat(NarratorInference.infer(nineteen, UK)).isEmpty();
    }

    // IF a character's own speech counted, THEN a woman speaking inside the quotes of a man's book would outvote him.
    @ParameterizedTest
    @ValueSource(
            strings = {
                "«Я пішла додому, — сказала вона.»",
                "— Я пішла додому, — сказала вона.",
                "Вона сказала: «Я знала цю дорогу дуже добре»."
            })
    void infer_feminineFormsOnlyInsideQuotesAndDialogue_areIgnored(final String speech) {
        final List<String> targets = twenty(join(take(MASCULINE, 9), Collections.nCopies(5, speech)));

        final Verdict verdict = NarratorInference.infer(targets, UK).orElseThrow();

        assertThat(verdict.gender()).isEqualTo(Gender.MALE);
        assertThat(verdict.forms()).isEqualTo(9);
        assertThat(verdict.minority()).isEmpty();
    }

    @Test
    void infer_onlyQuotedFormsAndNoNarratedOne_decidesNothing() {
        final List<String> targets = twenty(Collections.nCopies(12, "— Я пішов додому, — сказав він."));

        assertThat(NarratorInference.infer(targets, UK)).isEmpty();
    }

    // Past forms of other subjects and the narrator's present tense are no evidence of the narrator's gender.
    @Test
    void infer_otherSubjectsAndPresentTense_areNotCounted() {
        final List<String> noise = List.of(
                "Він пішов додому.",
                "Вона сказала правду.",
                "Я йду додому.",
                "Моя сестра пішла додому.",
                "Я бачу світло у вікні.");
        final List<String> targets = twenty(join(join(join(noise, noise), join(noise, noise)), take(MASCULINE, 3)));

        assertThat(NarratorInference.infer(targets, UK)).isEmpty();
    }

    @Test
    void infer_languageWithNoFirstPersonPastPattern_decidesNothing() {
        assertThat(NarratorInference.infer(twenty(take(MASCULINE, 10)), "xx")).isEmpty();
    }
}

package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.Severity;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.eval.MiniExpectations.CheckKind;
import ua.bookloom.pipeline.eval.MiniExpectations.Expectation;
import ua.bookloom.pipeline.eval.MiniExpectations.GlossaryExpectation;
import ua.bookloom.pipeline.eval.MiniRun.Calls;
import ua.bookloom.pipeline.eval.MiniRun.Outcome;

/** Each check of the mini suite can fail as well as pass: the scorer proven on hand-written runs. */
class MiniScorerTest {

    private static Outcome outcome(final String id, final String source, final String target) {
        return new Outcome(id, source, target, SegmentStatus.ACCEPTED, List.of());
    }

    static MiniRun run(final NamePolicy policy, final List<Outcome> outcomes, final List<GlossaryEntry> glossary) {
        return new MiniRun(
                policy,
                JobState.COMPLETED,
                outcomes.stream().collect(Collectors.toMap(Outcome::id, o -> o)),
                glossary,
                List.of(),
                new Calls(1, 1, 1),
                Duration.ofSeconds(1));
    }

    private static MiniResult scoreOne(final CheckKind check, final List<String> args, final Outcome... outcomes) {
        final String first = outcomes[0].id();
        final Expectation expectation = new Expectation("x", "d", first, List.of(), check, args, List.of());
        return MiniScorer.score(
                        new MiniExpectations(List.of(), List.of(expectation), List.of()),
                        run(NamePolicy.TRANSLITERATE, List.of(outcomes), List.of()))
                .get(0);
    }

    static MiniResult scoreGlossary(
            final GlossaryExpectation expectation, final NamePolicy policy, final GlossaryEntry... entries) {
        return MiniScorer.score(
                        new MiniExpectations(List.of(), List.of(), List.of(expectation)),
                        run(policy, List.of(), List.of(entries)))
                .get(0);
    }

    static GlossaryEntry entry(final String term, final String target, final TermType type, final Gender gender) {
        return GlossaryEntry.of("id-" + term, "p", term, target, type, gender, false);
    }

    @ParameterizedTest(name = "{0} {1} / {2} -> {3}")
    @CsvSource(
            delimiter = '|',
            quoteCharacter = '"',
            value = {
                "HEADING_NUMBER|3.|\"3. Кімната для ламп\"|true",
                "HEADING_NUMBER|3.|Кімната для ламп|false",
                "NEGATION_KEPT|не;2|\"Вона не знала й не спитала б.\"|true",
                "NEGATION_KEPT|не;2|\"Вона не знала, але спитала б.\"|false",
                "NUMBERS_KEPT|14|\"14 човнів витягли дев’ятнадцять сіток.\"|true",
                "NUMBERS_KEPT|14|\"Чотирнадцять човнів витягли дев’ятнадцять сіток.\"|false",
                "NO_STRESS_MARK||\"Пелл чекав біля комори для сіток.\"|true",
                "NO_STRESS_MARK||\"Пе\u0301лл чекав біля комори для сіток.\"|false",
                "PLACEHOLDERS|⟦g1⟧;⟦g2⟧|\"⟦g2⟧ і ⟦g1⟧\"|true",
                "PLACEHOLDERS|⟦g1⟧;⟦g2⟧|\"⟦g1⟧ і ⟦g1⟧\"|false"
            })
    void score_pairNeutralGrader_passesTheAuthorsLiteralAndFailsItsAbsence(
            final CheckKind check, final String args, final String target, final boolean passes) {
        final List<String> literal = Stream.of(Objects.toString(args, "").split(";"))
                .filter(a -> !a.isBlank())
                .toList();

        assertThat(scoreOne(check, literal, outcome("m1", "a", target)).passed())
                .isEqualTo(passes);
    }

    @ParameterizedTest(name = "{0} {2} / {3} -> {4}")
    @CsvSource(
            delimiter = '|',
            quoteCharacter = '"',
            value = {
                "NO_SOURCE_LINE|\"Нет, не сегодня.\"||\"Ні, не сьогодні.\"|true",
                "NO_SOURCE_LINE|\"Нет, не сегодня.\"||\"Нет, не сегодня.\"|false",
                "SOURCE_ONLY_WORDS|\"Она сказала, что придёт.\"|что|\"Вона сказала, що прийде.\"|true",
                "SOURCE_ONLY_WORDS|\"Она сказала, что придёт.\"|что|\"Вона сказала, что прийде.\"|false",
                "TARGET_ALPHABET|\"Он нашёл ключ.\"|uk|\"Він знайшов ключ.\"|true",
                "TARGET_ALPHABET|\"Он нашёл ключи.\"|uk|\"Він знайшов ключы.\"|false",
                "FOREIGN_SCRIPT|\"Он нашёл ключ.\"||\"Він знайшов ключ 鍵.\"|false",
                "FOREIGN_SCRIPT|\"Он нашёл ключ.\"||\"Він знайшов ключ KX-7.\"|true",
                "DIALOGUE_DASH|\"— Дверь открыта.\"||\"— Двері відчинені.\"|true",
                "DIALOGUE_DASH|\"— Дверь открыта.\"||\"Двері відчинені.\"|false",
                "VOCATIVE|\"— Иван, иди сюда.\"|Іване;Івасю|\"— Іване, іди сюди.\"|true",
                "VOCATIVE|\"— Иван, иди сюда.\"|Іване;Івасю|\"— Іван іде сюди.\"|false",
                "NARRATOR_GENDER|\"Я вернулась поздно.\"|повернулася;!повернувся|\"Я повернулася пізно.\"|true",
                "NARRATOR_GENDER|\"Я вернулась поздно.\"|повернулася;!повернувся|\"Я повернувся пізно.\"|false"
            })
    void score_ruGrader_passesTheAuthorsLiteralAndFailsItsAbsence(
            final CheckKind check, final String source, final String args, final String target, final boolean passes) {
        final List<String> literal = Stream.of(Objects.toString(args, "").split(";"))
                .filter(a -> !a.isBlank())
                .toList();

        assertThat(scoreOne(check, literal, outcome("m1", source, target)).passed())
                .isEqualTo(passes);
    }

    @ParameterizedTest(name = "{0} / {1} -> {2}")
    @CsvSource(
            delimiter = '|',
            value = {
                "Покличте Велемира.|Ключ Велемира лежав на столі.|true",
                "Покличте Велемира.|Ключ Велимира лежав на столі.|false"
            })
    void score_oneSpellingPerLemma_failsWhenTwoListedSpellingsAppearAcrossTheGroup(
            final String firstTarget, final String secondTarget, final boolean passes) {
        final Expectation expectation = new Expectation(
                "x",
                "d",
                null,
                List.of("a", "b"),
                CheckKind.ONE_SPELLING_PER_LEMMA,
                List.of("Велемир", "Велимир"),
                List.of());

        final MiniResult result = MiniScorer.score(
                        new MiniExpectations(List.of(), List.of(expectation), List.of()),
                        run(
                                NamePolicy.TRANSLITERATE,
                                List.of(outcome("a", "x", firstTarget), outcome("b", "x", secondTarget)),
                                List.of()))
                .get(0);

        assertThat(result.passed()).isEqualTo(passes);
    }

    @ParameterizedTest
    @CsvSource({
        "TARGET_ALPHABET,HARD",
        "FOREIGN_SCRIPT,HARD",
        "NO_SOURCE_LINE,HARD",
        "GLOSSARY_ONE_ENTRY,HARD",
        "GLOSSARY_GENDER,HARD",
        "ONE_SPELLING_PER_LEMMA,HARD",
        "SOURCE_ONLY_WORDS,SOFT",
        "DIALOGUE_DASH,SOFT",
        "VOCATIVE,SOFT",
        "NARRATOR_GENDER,SOFT",
        "ADDRESS_SAME,SOFT",
        "WINDOW_INJECTION,HARD"
    })
    void level_ruGraders_followTheDesignTable(final CheckKind check, final CheckKind.Level level) {
        assertThat(check.level()).isEqualTo(level);
    }

    private static final List<MiniGold.Entry> GOLD = List.of(
            new MiniGold.Entry("Ilsabet Corwen", List.of("Ilsabet Corwen", "Ilsabet"), "Ілсабет Корвен", Gender.FEMALE),
            new MiniGold.Entry("Dorrin Vask", List.of("Dorrin Vask", "Dorrin"), "Дорін Васк", Gender.MALE));

    private static CaseRecord.Exchange glossaryCall(final String items) {
        return new CaseRecord.Exchange(
                "[system] Translate.\n[user] [Glossary — apply these renderings exactly]\n"
                        + "Ilsabet Corwen → Ілсабет Корвен (character, female)\n"
                        + "Dorrin Vask → Дорін Васк (character, male)\n\n<Items>\n"
                        + items + "\n</Items>\n\nReminder.",
                "{}");
    }

    @Test
    void windowInjection_entryNamedOutsideItsWindow_fails() {
        // A glossary line for Dorrin Vask in a call whose items never mention him is an injection.
        final MiniScorer.Verdict verdict = MiniGraders.windowInjection(
                List.of(glossaryCall("<s id=\"1\">Ilsabet locked the door.</s>")),
                List.of("Ilsabet locked the door."),
                GOLD);

        assertThat(verdict.ok()).isFalse();
        assertThat(verdict.detail()).contains("Dorrin Vask").contains("mapChecked=2 mapOutside=1");
    }

    @Test
    void windowInjection_everyEntryNamedInItsWindow_passes() {
        // The control: both names occur in the items, so nothing is outside.
        final MiniScorer.Verdict verdict = MiniGraders.windowInjection(
                List.of(glossaryCall("<s id=\"1\">Ilsabet locked the door; Dorrin knocked.</s>")),
                List.of("Ilsabet locked the door; Dorrin knocked."),
                GOLD);

        assertThat(verdict.ok()).isTrue();
        assertThat(verdict.detail()).contains("mapChecked=2 mapOutside=0");
    }

    @Test
    void windowInjection_blockEightNamesAPersonTheBookHasNotSaidYet_fails() {
        // The summary mentions Dorrin, who first appears in the second block, after the window's end.
        final CaseRecord.Exchange call = new CaseRecord.Exchange("""
                [system] x
                [user] [Book so far — context only]
                Dorrin arrived at the gate.

                <Items>
                <s id="1">Ilsabet locked the door.</s>
                </Items>""", "{}");

        final MiniScorer.Verdict verdict = MiniGraders.windowInjection(
                List.of(call), List.of("Ilsabet locked the door.", "Dorrin knocked."), GOLD);

        assertThat(verdict.ok()).isFalse();
        assertThat(verdict.detail()).contains("block8Checked=1 block8Outside=1");
    }

    @Test
    void score_latinWordLeft_failsNoLatinExceptAndNamesIt() {
        final MiniResult result =
                scoreOne(CheckKind.NO_LATIN_EXCEPT, List.of("KX-7"), outcome("m1", "a", "Насос KX-7 і Velmora"));

        assertThat(result.passed()).isFalse();
        assertThat(result.detail()).contains("Velmora");
    }

    @Test
    void score_onlyTheAllowedCodeIsLatin_passesNoLatinExcept() {
        assertThat(scoreOne(CheckKind.NO_LATIN_EXCEPT, List.of("KX-7"), outcome("m1", "a", "Табличка KX-7 ще блищала."))
                        .passed())
                .isTrue();
    }

    @Test
    void score_russianLetterInTarget_failsNoRussianLetters() {
        assertThat(scoreOne(CheckKind.NO_RUSSIAN_LETTERS, List.of(), outcome("m1", "a", "Підзываючи його"))
                        .passed())
                .isFalse();
        assertThat(scoreOne(CheckKind.NO_RUSSIAN_LETTERS, List.of(), outcome("m1", "a", "Підзиваючи його"))
                        .passed())
                .isTrue();
    }

    @Test
    void score_flaggedSegment_failsNotFlagged() {
        final Outcome flagged = new Outcome("m1", "a", "текст", SegmentStatus.FLAGGED, List.of());

        assertThat(scoreOne(CheckKind.NOT_FLAGGED, List.of(), flagged).passed()).isFalse();
        assertThat(scoreOne(CheckKind.NOT_FLAGGED, List.of(), outcome("m1", "a", "текст"))
                        .passed())
                .isTrue();
    }

    @Test
    void score_storedSentenceCountFinding_failsNoFinding() {
        final Outcome withFinding = new Outcome(
                "m1",
                "a",
                "текст",
                SegmentStatus.ACCEPTED,
                List.of(new QaFinding("omission", Severity.LOW, "one sentence short", "sentence-count")));

        assertThat(scoreOne(CheckKind.NO_FINDING, List.of("sentence-count"), withFinding)
                        .passed())
                .isFalse();
        assertThat(scoreOne(CheckKind.NO_FINDING, List.of("name-swap"), withFinding)
                        .passed())
                .isTrue();
    }

    @Test
    void score_feminineFormAfterYa_failsNarratorMale() {
        assertThat(scoreOne(CheckKind.NARRATOR_MALE, List.of(), outcome("m1", "a", "Я приїхала на поромі."))
                        .passed())
                .isFalse();
        assertThat(scoreOne(CheckKind.NARRATOR_MALE, List.of(), outcome("m1", "a", "Я приїхав на поромі."))
                        .passed())
                .isTrue();
    }

    @Test
    void score_aOneWordReplyToALongParagraph_failsMinRatio() {
        final String source = "The harbour office had been built in the year of the great frost.";

        assertThat(scoreOne(CheckKind.MIN_RATIO, List.of("0.6"), outcome("m1", source, "Ананас"))
                        .passed())
                .isFalse();
        assertThat(scoreOne(
                                CheckKind.MIN_RATIO,
                                List.of("0.6"),
                                outcome("m1", source, "Контору гавані збудували у лютий мороз."))
                        .passed())
                .isTrue();
    }

    private static MiniResult scoreAddress(final String first, final String second) {
        final Expectation group =
                new Expectation("x", "d", null, List.of("m1", "m2"), CheckKind.ADDRESS_SAME, List.of(), List.of());
        return MiniScorer.score(
                        new MiniExpectations(List.of(), List.of(group), List.of()),
                        run(
                                NamePolicy.TRANSLITERATE,
                                List.of(outcome("m1", "a", first), outcome("m2", "b", second)),
                                List.of()))
                .get(0);
    }

    @Test
    void score_tiAndViTogetherAcrossAGroup_failsAddressSame() {
        final MiniResult mixed = scoreAddress("Ти не повинен тут бути.", "Ви маєте моє слово.");
        final MiniResult formal = scoreAddress("Ви не повинні тут бути.", "Ви маєте моє слово.");

        assertThat(mixed.passed()).isFalse();
        assertThat(formal.passed()).isTrue();
    }

    @Test
    void score_matchesAndAbsent_passOnlyWhenThePatternIsThereOrNot() {
        final Outcome target = outcome("m1", "a", "Насос KX-7");

        assertThat(scoreOne(CheckKind.MATCHES, List.of("KX-7"), target).passed())
                .isTrue();
        assertThat(scoreOne(CheckKind.MATCHES, List.of("Velmora"), target).passed())
                .isFalse();
        assertThat(scoreOne(CheckKind.ABSENT, List.of("^\\W*[\\p{L}]{1,14}\\W*$"), outcome("m1", "a", "Ананас"))
                        .passed())
                .isFalse();
        assertThat(scoreOne(CheckKind.ABSENT, List.of("Velmora"), target).passed())
                .isTrue();
    }

    @Test
    void score_segmentTheRunNeverDecided_failsNamingIt() {
        final MiniResult result = scoreOne(CheckKind.NOT_FLAGGED, List.of(), outcome("m9", "a", "текст"));
        final Expectation missing =
                new Expectation("x", "d", "m1", List.of(), CheckKind.NOT_FLAGGED, List.of(), List.of());

        final MiniResult absent = MiniScorer.score(
                        new MiniExpectations(List.of(), List.of(missing), List.of()),
                        run(NamePolicy.TRANSLITERATE, List.of(outcome("m9", "a", "текст")), List.of()))
                .get(0);

        assertThat(result.passed()).isTrue();
        assertThat(absent.passed()).isFalse();
        assertThat(absent.detail()).contains("m1");
    }

    @Test
    void score_expectationOfAnotherNamePolicy_isLeftOutNotPassed() {
        final Expectation keepOnly = new Expectation(
                "x", "d", "m1", List.of(), CheckKind.MATCHES, List.of("Velmora"), List.of(NamePolicy.KEEP_ORIGINAL));
        final MiniExpectations expectations = new MiniExpectations(List.of(), List.of(keepOnly), List.of());
        final List<Outcome> outcomes = List.of(outcome("m1", "a", "Годинник Вельмора"));

        assertThat(MiniScorer.score(expectations, run(NamePolicy.TRANSLITERATE, outcomes, List.of())))
                .isEmpty();
        assertThat(MiniScorer.score(expectations, run(NamePolicy.KEEP_ORIGINAL, outcomes, List.of())))
                .extracting(MiniResult::passed)
                .containsExactly(false);
    }

    @Test
    void score_everyBlockCheck_namesTheBlocksThatFail() {
        final Expectation every =
                new Expectation("every", "d", null, List.of(), CheckKind.NO_RUSSIAN_LETTERS, List.of(), List.of());

        final MiniResult result = MiniScorer.score(
                        new MiniExpectations(List.of(every), List.of(), List.of()),
                        run(
                                NamePolicy.TRANSLITERATE,
                                List.of(outcome("m1", "a", "добре"), outcome("m2", "b", "сынок")),
                                List.of()))
                .get(0);

        assertThat(result.passed()).isFalse();
        assertThat(result.detail()).isEqualTo("failing: m2");
    }
}

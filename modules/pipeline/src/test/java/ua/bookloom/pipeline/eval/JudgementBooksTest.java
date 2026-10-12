package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.pipeline.BriefSuggestion;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.NarratorHint;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.pipeline.SequenceJobs;
import ua.bookloom.pipeline.SequenceJobs.Prepared;
import ua.bookloom.pipeline.glossary.PronounGender;
import ua.bookloom.pipeline.narrator.NarratorDetector;
import ua.bookloom.pipeline.prompt.PromptName;

/**
 * The committed judgement books (15h.E3) against the code that judges a book with no model: the narrator detector, the
 * pronoun reader, the brief's samples and the brief's check of the narrator's gender quote. Each book is a shape the
 * model's judgement failed on or could fail on; what the code must conclude is in the book's {@code expect}.
 */
class JudgementBooksTest {

    private static final String SOURCE = "en";

    @TempDir
    private Path dir;

    static Stream<JudgementBook> books() {
        return JudgementBook.all().stream();
    }

    static Stream<JudgementBook> booksTheCodeCanFullyVerify() {
        return books().filter(book -> book.expect().verifiedGender() == null);
    }

    static Stream<Arguments> pronounNames() {
        return books().flatMap(book -> book.expect().pronouns().entrySet().stream()
                .map(name -> Arguments.of(book.id(), name.getKey(), name.getValue())));
    }

    private Prepared imported(final JudgementBook book) {
        return SequenceJobs.importBook(
                book.write(dir), SequenceJobs.brief(SOURCE, "uk", QualityDial.FAST, Narrator.unspecified()));
    }

    private static List<String> briefSamples(final StageProbe probe) {
        return probe.requests().stream()
                .filter(request -> request.responseFormat() != null
                        && request.responseFormat().name().equals(PromptName.BRIEF_SUGGESTION.responseFormatName()))
                .map(ChatRequest::messages)
                .map(messages -> messages.getLast().content())
                .toList();
    }

    @Test
    void books_coverEveryShapeTheTaskNames() {
        assertThat(JudgementBook.all())
                .extracting(JudgementBook::id)
                .containsExactly(
                        "gender-late-or-object",
                        "straight-quoted-speech",
                        "vocative-name",
                        "female-narrator-address",
                        "front-matter-converter",
                        "alternating-narrators",
                        "letter-in-third-person",
                        "first-person-vocative-name",
                        "quoted-woman-not-the-narrator");
        assertThat(JudgementBook.all())
                .allSatisfy(book -> assertThat(book.gold().characters()).isNotEmpty());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("books")
    void narratorDetector_book_readsTheExpectedPerson(final JudgementBook book) {
        final String detected = NarratorDetector.detect(imported(book).document(), SOURCE)
                .map(NarratorHint::person)
                .map(Enum::name)
                .orElse("NONE");

        assertThat(detected).isEqualTo(book.expect().narrator());
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("pronounNames")
    void pronounGender_name_decidesTheExpectedGender(final String id, final String term, final Gender expected) {
        final PronounGender.Evidence evidence =
                PronounGender.of(term, JudgementBook.byId(id).paragraphs(), SOURCE);

        assertThat(evidence.dominant().orElse(Gender.UNKNOWN))
                .as(evidence.compact())
                .isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("books")
    void briefWindows_book_startAtTheStoryAndHoldNothingBeforeIt(final JudgementBook book) {
        final StageProbe probe = new StageProbe(new JudgementFakeModel(book, Set.of()));

        new JudgementRunner(probe).judge(book.id(), book.write(dir), book.gold());

        final List<String> samples = briefSamples(probe);
        assertThat(samples).hasSizeBetween(1, 5);
        assertThat(samples.getFirst()).contains("Passage 1: " + book.expect().sampleStartsWith());
        assertThat(samples)
                .allSatisfy(sample -> assertThat(book.expect().sampleNever()).noneMatch(sample::contains));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("books")
    void briefSuggestion_goodModel_keepsOnlyTheNarratorGenderTheCodeVerifies(final JudgementBook book) {
        final StageProbe probe = new StageProbe(new JudgementFakeModel(book, Set.of()));

        final BriefSuggestion brief = new JudgementRunner(probe)
                .judge(book.id(), book.write(dir), book.gold())
                .brief();

        assertThat(brief).isNotNull();
        assertThat(brief.narratorGender())
                .isEqualTo(Objects.requireNonNullElse(
                        book.expect().verifiedGender(), book.gold().brief().narratorGender()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("booksTheCodeCanFullyVerify")
    void judgement_goodModel_scoresEveryGoldFieldRight(final JudgementBook book) {
        final JudgementRunner runner = new JudgementRunner(new JudgementFakeModel(book, Set.of()));

        final JudgementReport.Book scored = JudgementReport.score(
                book.id(), book.gold(), List.of(runner.judge(book.id(), book.write(dir), book.gold())));

        assertThat(scored.items())
                .as(scored.items().toString())
                .allSatisfy(item -> assertThat(item.accuracy()).isEqualTo(1.0));
    }

    // IF the first-person letter in a third-person novel were read as the book's narration, THEN the detector would
    // call the book first person or mixed and the brief would be told so.
    @Test
    void narratorDetector_letterInsideAThirdPersonNovel_answersThird() {
        final JudgementBook book = JudgementBook.byId("letter-in-third-person");

        final String detected = NarratorDetector.detect(imported(book).document(), SOURCE)
                .map(NarratorHint::person)
                .map(Enum::name)
                .orElse("NONE");

        assertThat(detected).isEqualTo("THIRD");
        assertThat(book.paragraphs()).anyMatch(paragraph -> paragraph.contains("I write from the harbour town"));
    }

    // IF a narrator named only by others' vocatives were scored as verified before her name is a glossary character,
    // THEN the report would hide the gap 15i.T5 closes: today only the narrator's gender is missed.
    @Test
    void judgement_vocativeNamedNarratorWithAnEmptyGlossary_missesOnlyTheNarratorGender() {
        final JudgementBook book = JudgementBook.byId("first-person-vocative-name");
        final JudgementRunner runner = new JudgementRunner(new JudgementFakeModel(book, Set.of()));

        final JudgementReport.Book scored = JudgementReport.score(
                book.id(), book.gold(), List.of(runner.judge(book.id(), book.write(dir), book.gold())));

        assertThat(scored.items())
                .extracting(JudgementReport.Item::name, JudgementReport.Item::accuracy)
                .contains(
                        tuple("genre", 1.0),
                        tuple("register", 1.0),
                        tuple("narrator", 1.0),
                        tuple("narratorGender", 0.0),
                        tuple("Thessaly:gender", 1.0));
    }

    // IF a woman the male narrator speaks to by name were taken for the narrator, THEN the brief would carry a female
    // narrator; the good model quotes how he is addressed ('sir') and the brief ends male.
    @Test
    void briefSuggestion_maleNarratorWhoNamesAWomanInHisSpeech_endsMale() {
        final JudgementBook book = JudgementBook.byId("quoted-woman-not-the-narrator");
        final StageProbe probe = new StageProbe(new JudgementFakeModel(book, Set.of()));

        final BriefSuggestion brief = new JudgementRunner(probe)
                .judge(book.id(), book.write(dir), book.gold())
                .brief();

        assertThat(brief).isNotNull();
        assertThat(brief.narratorGender()).isEqualTo(Gender.MALE);
        assertThat(book.paragraphs()).anyMatch(paragraph -> paragraph.contains("Mind the step, Lucy"));
    }

    // Every case added for the new prediction states the gold of each field it is scored on.
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"letter-in-third-person", "first-person-vocative-name", "quoted-woman-not-the-narrator"})
    void books_newBriefCase_haveAGoldForGenreRegisterNarratorAndGender(final String id) {
        final JudgementGold.Brief gold = JudgementBook.byId(id).gold().brief();

        assertThat(gold.genreClass()).isNotEmpty();
        assertThat(gold.register()).isNotNull();
        assertThat(gold.narrator()).isNotEqualTo(NarratorPerson.UNSPECIFIED);
        assertThat(gold.narratorGender()).isNotNull();
    }
}

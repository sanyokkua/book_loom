package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.pipeline.QualityDial;

/**
 * The names suite's runner (15i.E1) driven offline: Names &amp; style runs over each committed book through the real
 * {@code GlossaryService} and {@code LexiconService} with a scripted model, and the final glossary and lexicon are
 * scored against the book's gold. A book marked a known failure must still end wrong, so the test fails the day a
 * task fixes it and the flag is dropped; every other book must end clean.
 */
class NamesRunnerTest {

    @TempDir
    private Path dir;

    static Stream<Arguments> knownFailureBooks() {
        return NamesBook.all().stream().filter(NamesBook::knownFailure).map(book -> Arguments.of(book.id(), book));
    }

    static Stream<Arguments> cleanBooks() {
        return NamesBook.all().stream().filter(book -> !book.knownFailure()).map(book -> Arguments.of(book.id(), book));
    }

    private NamesRow run(final NamesBook book) {
        return new NamesRunner(new NamesFakeModel(book)).run(book, dir);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cleanBooks")
    void run_bookNotMarkedKnownFailure_endsCleanWithAModelThatFollowsTheGold(final String id, final NamesBook book) {
        final NamesRow row = run(book);

        assertThat(row.error()).as(id + " error").isNull();
        assertThat(row.score()).as(id).isNotNull();
        assertThat(row.score().isClean()).as(id + " " + row.score().json()).isTrue();
    }

    // Since 15i.N5 no committed book is a known failure; the test stays for the next shape a task records as red.
    @ParameterizedTest(name = "{0}", allowZeroInvocations = true)
    @MethodSource("knownFailureBooks")
    void run_bookMarkedKnownFailure_stillEndsWrong(final String id, final NamesBook book) {
        final NamesRow row = run(book);

        assertThat(row.error()).as(id + " error").isNull();
        assertThat(row.score()).as(id).isNotNull();
        assertThat(row.score().isClean())
                .as(id + " now ends clean: drop knownFailure from the book (task " + book.fixedBy() + ") "
                        + row.score().json())
                .isFalse();
    }

    // IF the book's own pronoun tally (she x5, he x9) did not outweigh the model's windows, THEN the man would end with
    // a woman's gender that nothing marks as a guess; with the tally he ends male, and suggested at worst (15i.N1).
    @Test
    void run_misattributedShe_corinEndsMaleAndNoWrongGenderIsVerified() {
        final NamesScore score = run(NamesBook.byId("misattributed-she")).score();

        assertThat(score.wrongButVerified()).isZero();
        assertThat(score.eligible()).isEqualTo(2);
        assertThat(score.genderCorrect()).isEqualTo(2);
    }

    // IF the scan dropped the bartender's name for the common word it is also written as, THEN the one real name the
    // gold keeps would be lost and the man scored wrong for gender and type: the name is read in context and kept as a
    // man, and the common "mark" is no name and no recurring term (15i.N5).
    @Test
    void run_bartenderMark_keepsMarkAsAManAndTheCommonMarkIsNoTerm() {
        final NamesScore score = run(NamesBook.byId("bartender-mark")).score();

        assertThat(score.lostNames()).isEmpty();
        assertThat(score.eligible()).isEqualTo(2);
        assertThat(score.genderCorrect()).isEqualTo(2);
        assertThat(score.typeCorrect()).isEqualTo(2);
        assertThat(score.wrongButVerified()).isZero();
        assertThat(score.junkKept()).isZero();
        assertThat(score.recurringJunkKept()).isZero();
    }

    // IF the short and the full spelling kept their pronouns apart, THEN three "she" and two "she" would each be too
    // little to decide, and a model unsure of the short form would leave one woman with two genders; pooled, five of
    // five decide both with no model (15i.N2).
    @Test
    void run_shortAndFullNameWithWeakEvidenceEach_endAsOneFemalePerson() {
        final NamesScore score = run(NamesBook.byId("short-and-full-weak")).score();

        assertThat(score.eligible()).isEqualTo(3);
        assertThat(score.genderCorrect()).isEqualTo(3);
        assertThat(score.wrongButVerified()).isZero();
        assertThat(score.clusterRate()).isEqualTo(1.0);
    }

    // IF the recurring term were read as one more name to drop, THEN the distress call would be lost from the book (it
    // is
    // no name, so the glossary drops it); at Balanced the lexicon reads it in its windows, sees one sense in all of
    // them
    // and keeps it with the rendering the book will use (15i.N7).
    @Test
    void run_maydayAtBalanced_keepsTheDistressCallAsATermWithItsRendering() {
        final NamesBook book = NamesBook.byId("mayday");
        final NamesRunner runner = new NamesRunner(new NamesFakeModel(book), QualityDial.BALANCED);

        final NamesRow row = runner.run(book, dir);

        assertThat(row.error()).isNull();
        assertThat(row.score().recurringKept()).isEqualTo(1);
        assertThat(row.score().recurringDropped()).isZero();
        assertThat(row.score().recurringJunkKept()).isZero();
        assertThat(row.score().junkKept()).isZero();
        assertThat(runner.lexicon())
                .filteredOn(entry -> entry.term().equalsIgnoreCase("mayday"))
                .singleElement()
                .satisfies(entry -> assertThat(entry.chosen()).isEqualTo("мейдей"));
    }

    // At Fast the one-sentence batch choice keeps it too, with no rendering written by the choice itself.
    @Test
    void run_maydayAtFast_keepsTheDistressCallAsATerm() {
        final NamesBook book = NamesBook.byId("mayday");

        final NamesRow row = new NamesRunner(new NamesFakeModel(book), QualityDial.FAST).run(book, dir);

        assertThat(row.error()).isNull();
        assertThat(row.score().recurringKept()).isEqualTo(1);
        assertThat(row.score().recurringJunkKept()).isZero();
    }

    @Test
    void run_anyBook_countsTheModelCallsItSentAndTimesTheRun() {
        final NamesScore score = run(NamesBook.byId("stray-he")).score();

        assertThat(score.calls()).isPositive();
        assertThat(score.minutes()).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void run_providerThatFailsEveryCall_returnsARowNamingTheErrorCodeAndNoScore() {
        final ChatModel failing = request -> Result.err(AppError.of(ErrorCode.unreachable, "Offline", "no provider"));
        final NamesBook book = NamesBook.byId("stray-he");

        final NamesRow row = new NamesRunner(failing).run(book, dir);

        assertThat(row.book()).isEqualTo("stray-he");
        assertThat(row.error()).isEqualTo("unreachable");
        assertThat(row.score()).isNull();
    }
}

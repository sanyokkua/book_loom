package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

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

    @ParameterizedTest(name = "{0}")
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

    // IF a model that cites windows with a 'she' for a man is believed, THEN the man ends with a woman's gender that
    // nothing marks as a guess; this is the owner's run failure, fixed by 15i.N1.
    @Test
    void run_misattributedShe_corinEndsWithAWrongGenderThatIsNotMarkedSuggested() {
        final NamesBook book = NamesBook.byId("misattributed-she");
        assumeThat(book.knownFailure()).isTrue();

        final NamesScore score = run(book).score();

        assertThat(score.wrongButVerified()).isEqualTo(1);
        assertThat(score.eligible()).isEqualTo(2);
        assertThat(score.genderCorrect()).isEqualTo(1);
        assertThat(score.genderAccuracy()).isEqualTo(0.5);
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

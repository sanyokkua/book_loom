package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.glossary.GlossaryServiceFixtures.PROJECT;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.ModelCallStarted;
import ua.bookloom.api.pipeline.NamesEstimate;
import ua.bookloom.api.pipeline.NamesPass;
import ua.bookloom.api.pipeline.NamesPassReport;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.api.project.NamePolicy;

/**
 * What the names pass does when it cannot finish (15i.N8): a failed call, a cancelled reply, a Stop, a refused project,
 * and what it says before its first call. Whatever stops it, neither stored table changes.
 */
class NamesPassStopTest extends NamesPassTestBase {
    private static final AppError UNREACHABLE =
            AppError.of(ErrorCode.unreachable, "Provider unreachable", "The provider did not answer.");
    private static final AppError CANCELLED = AppError.of(ErrorCode.cancelled, "Stopped", "The call was stopped.");

    // A call that fails, in any step, ends the pass with that call's error and changes neither table.
    @ParameterizedTest
    @ValueSource(strings = {"review-terms", "term-reading", "lexicon-reading", "suggest-targets"})
    void run_callFailsInAStep_answersItsErrorAndChangesNothing(final String format) {
        seeded("uk", NamePolicy.TRANSLITERATE);
        final List<GlossaryEntry> glossaryBefore = fixture.glossaryNow();
        final List<LexiconEntry> lexiconBefore = fixture.lexiconNow();

        final Result<NamesPassReport> result = run(model().failingFirst(format, UNREACHABLE));

        assertThat(Objects.requireNonNull(result.error()).code()).isEqualTo(ErrorCode.unreachable);
        assertThat(fixture.glossaryNow()).isEqualTo(glossaryBefore);
        assertThat(fixture.lexiconNow()).isEqualTo(lexiconBefore);
    }

    // The person presses Stop: the call in flight answers "cancelled", and nothing the earlier steps found is kept.
    @Test
    void run_modelAnswersCancelledAfterTheNamesWereDecided_changesNothing() {
        seeded("uk", NamePolicy.TRANSLITERATE);
        final List<GlossaryEntry> glossaryBefore = fixture.glossaryNow();
        final List<LexiconEntry> lexiconBefore = fixture.lexiconNow();

        final Result<NamesPassReport> result = run(model().failingFirst("lexicon-reading", CANCELLED));

        assertThat(Objects.requireNonNull(result.error()).code()).isEqualTo(ErrorCode.cancelled);
        assertThat(fixture.glossaryNow()).isEqualTo(glossaryBefore);
        assertThat(fixture.lexiconNow()).isEqualTo(lexiconBefore);
    }

    // Stop interrupts the calling thread during the fourth call: that call completes, no fifth goes out, and nothing
    // is written, though every call that was made answered.
    @Test
    void run_callingThreadInterruptedMidPass_answersCancelledAndChangesNothing() {
        seeded("uk", NamePolicy.TRANSLITERATE);
        final List<GlossaryEntry> glossaryBefore = fixture.glossaryNow();
        final List<LexiconEntry> lexiconBefore = fixture.lexiconNow();
        final AtomicInteger started = new AtomicInteger();
        final NamesPassModel model = model();

        final Result<NamesPassReport> result = fixture.pass().run(PROJECT, model, null, event -> {
            if (event instanceof ModelCallStarted && started.incrementAndGet() == 4) {
                Thread.currentThread().interrupt();
            }
        });

        assertThat(Objects.requireNonNull(result.error()).code()).isEqualTo(ErrorCode.cancelled);
        assertThat(model.requests()).isEqualTo(4);
        assertThat(fixture.glossaryNow()).isEqualTo(glossaryBefore);
        assertThat(fixture.lexiconNow()).isEqualTo(lexiconBefore);
    }

    // The same refusals the buttons give, before any call.
    @Test
    void run_projectWithNoTargetLanguage_answersValidationAndCallsNothing() {
        fixture.open(BookBrief.defaults("en"));
        final NamesPassModel model = model();

        final Result<NamesPassReport> result = run(model);

        assertThat(Objects.requireNonNull(result.error()).code()).isEqualTo(ErrorCode.validation);
        assertThat(model.requests()).isZero();
    }

    @Test
    void run_unknownProject_answersValidationAndCallsNothing() {
        final NamesPassModel model = model();

        final Result<NamesPassReport> result = fixture.pass().run("ghost", model, null, events::add);

        assertThat(Objects.requireNonNull(result.error()).code()).isEqualTo(ErrorCode.validation);
        assertThat(model.requests()).isZero();
    }

    // Before the first call the pass says what the dial's plan will read: five terms (the book names each at least
    // three times) in one call each, about one to two minutes at Balanced.
    @Test
    void estimate_seededBookAtBalanced_isFiveCallsAndOneToTwoMinutes() {
        seeded("uk", NamePolicy.TRANSLITERATE);

        final Result<NamesEstimate> estimate = fixture.pass().estimate(PROJECT);

        assertThat(estimate.data()).isEqualTo(new NamesEstimate(5, 1, 2));
    }

    @Test
    void estimate_unknownProject_answersValidation() {
        assertThat(Objects.requireNonNull(fixture.pass().estimate("ghost").error())
                        .code())
                .isEqualTo(ErrorCode.validation);
    }

    @Test
    void pass_injector_buildsTheNamesPassFromThePipelineModule() {
        assertThat(fixture.injector.getInstance(NamesPass.class)).isInstanceOf(NamesPassImpl.class);
    }
}

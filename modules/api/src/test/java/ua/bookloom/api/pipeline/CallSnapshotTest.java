package ua.bookloom.api.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.llm.TokenUsage;

/** One model call as the live panel shows it: what went out, the state it is in, the reply and what came of it. */
class CallSnapshotTest {

    private static final Instant AT = Instant.parse("2026-10-08T10:00:00Z");
    private static final CallSegment FIRST = new CallSegment("Book.md:0", "ch1 · p01", "He left.");
    private static final PromptSection STYLE =
            new PromptSection("styleSheet", "Style:", PromptSection.Origin.SYSTEM, List.of("Plain prose."));

    private static CallSnapshot waiting() {
        return CallSnapshot.waiting(
                7, CallKind.DRAFT, "draft-batch-json", null, List.of(FIRST), List.of(STYLE), AT, 1, 2, null);
    }

    @Test
    void waiting_firstAttempt_hasNoReplyNoUsageAndNoOutcomes() {
        final CallSnapshot snapshot = waiting();

        assertThat(snapshot.state()).isEqualTo(CallState.WAITING);
        assertThat(snapshot.reply()).isNull();
        assertThat(snapshot.usage()).isNull();
        assertThat(snapshot.outcomes()).isEmpty();
        assertThat(snapshot.elapsed()).isEqualTo(Duration.ZERO);
    }

    @Test
    void answered_waitingCall_keepsItsIdAndSectionsAndHoldsTheReply() {
        final TokenUsage usage = new TokenUsage(100, 20, Duration.ofSeconds(2));

        final CallSnapshot answered = waiting().answered("{\"items\":[]}", usage, Duration.ofSeconds(3));

        assertThat(answered.callId()).isEqualTo(7);
        assertThat(answered.state()).isEqualTo(CallState.ANSWERED);
        assertThat(answered.reply()).isEqualTo("{\"items\":[]}");
        assertThat(answered.usage()).isEqualTo(usage);
        assertThat(answered.elapsed()).isEqualTo(Duration.ofSeconds(3));
        assertThat(answered.sections()).containsExactly(STYLE);
    }

    @Test
    void retrying_failedFirstAttempt_waitsAgainWithTheNextAttempt() {
        final CallSnapshot failed = waiting().ended(CallState.FAILED, Duration.ofSeconds(90));

        final CallSnapshot again = failed.retrying(AT.plusSeconds(91), 2, 2, Duration.ofSeconds(90));

        assertThat(again.state()).isEqualTo(CallState.WAITING);
        assertThat(again.attempt()).isEqualTo(2);
        assertThat(again.startedAt()).isEqualTo(AT.plusSeconds(91));
        assertThat(again.elapsed()).isEqualTo(Duration.ZERO);
    }

    @ParameterizedTest
    @EnumSource(
            value = CallState.class,
            names = {"WAITING", "ANSWERED"})
    void ended_stateThatIsNoFailure_isRejected(final CallState state) {
        assertThatIllegalArgumentException().isThrownBy(() -> waiting().ended(state, Duration.ZERO));
    }

    @Test
    void withOutcome_answeredCall_appendsTheNoteInOrder() {
        final SegmentOutcomeNote adopted = new SegmentOutcomeNote("Book.md:0", SegmentOutcomeNote.Kind.ADOPTED, "");
        final SegmentOutcomeNote accepted = new SegmentOutcomeNote("Book.md:0", SegmentOutcomeNote.Kind.ACCEPTED, "");

        final CallSnapshot noted = waiting()
                .answered("x", null, Duration.ZERO)
                .withOutcome(adopted)
                .withOutcome(accepted);

        assertThat(noted.outcomes()).containsExactly(adopted, accepted);
    }

    @Test
    void waiting_listsTheCallerKeepsChanging_areCopied() {
        final List<CallSegment> segments = new ArrayList<>(List.of(FIRST));
        final CallSnapshot snapshot =
                CallSnapshot.waiting(1, CallKind.REVIEW, "reviewer", null, segments, List.of(), AT, 1, 1, null);
        segments.clear();

        assertThat(snapshot.segments()).containsExactly(FIRST);
    }

    @Test
    void waiting_attemptBeyondItsMaximum_isRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> CallSnapshot.waiting(
                        1, CallKind.REVIEW, "reviewer", null, List.of(), List.of(), AT, 3, 2, null));
    }

    @Test
    void promptSection_lines_areCopied() {
        final List<String> lines = new ArrayList<>(List.of("a", "b"));
        final PromptSection section = new PromptSection("glossaryTerms", "Glossary", PromptSection.Origin.USER, lines);
        lines.clear();

        assertThat(section.lines()).containsExactly("a", "b");
    }

    @Test
    void callSnapshotUpdated_holdsTheSnapshot() {
        final CallSnapshot snapshot = waiting();

        assertThat(new CallSnapshotUpdated(snapshot).snapshot()).isSameAs(snapshot);
    }

    @ParameterizedTest
    @EnumSource(
            value = CallState.class,
            names = {"FAILED", "CANCELLED"})
    void ended_failureState_endsTheCallWithNoReply(final CallState state) {
        final CallSnapshot ended = waiting().ended(state, Duration.ofSeconds(4));

        assertThat(ended.state()).isEqualTo(state);
        assertThat(ended.reply()).isNull();
        assertThat(ended.elapsed()).isEqualTo(Duration.ofSeconds(4));
    }

    @Test
    void waiting_callIdZero_isRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> CallSnapshot.waiting(
                        0, CallKind.REVIEW, "reviewer", null, List.of(), List.of(), AT, 1, 1, null));
    }

    @Test
    void waiting_attemptZero_isRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> CallSnapshot.waiting(
                        1, CallKind.REVIEW, "reviewer", null, List.of(), List.of(), AT, 0, 1, null));
    }

    @ParameterizedTest
    @CsvSource({"ADOPTED,false", "FELL_BACK,false", "ACCEPTED,true", "FLAGGED,true"})
    void isDecision_eachKind_isTrueOnlyForADecision(final SegmentOutcomeNote.Kind kind, final boolean decision) {
        assertThat(new SegmentOutcomeNote("s-1", kind, "").isDecision()).isEqualTo(decision);
    }
}

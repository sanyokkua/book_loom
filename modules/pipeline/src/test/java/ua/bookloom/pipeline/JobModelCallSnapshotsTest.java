package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.CallSegment;
import ua.bookloom.api.pipeline.CallSnapshot;
import ua.bookloom.api.pipeline.CallSnapshotUpdated;
import ua.bookloom.api.pipeline.CallState;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.PromptSection;
import ua.bookloom.api.pipeline.SegmentOutcomeNote;
import ua.bookloom.api.project.SegmentLocator;
import ua.bookloom.pipeline.context.ContextBudget;
import ua.bookloom.pipeline.prompt.CallDescriptor;
import ua.bookloom.pipeline.run.JobModelCalls;

/** A described call is shown as one snapshot that waits, then answers, fails or is cancelled, and gathers outcomes. */
class JobModelCallSnapshotsTest {

    private static final ChatRequest REQUEST = new ChatRequest(List.of(new ChatMessage(ChatRole.USER, "Hello.")));
    private static final List<String> IDS = List.of("Book.md:0", "Book.md:1");
    private static final PromptSection ITEMS =
            new PromptSection("items", "<Items>", PromptSection.Origin.USER, List.of("<s id=\"1\">He left.</s>"));
    private static final CallDescriptor BATCH =
            new CallDescriptor("draft-batch-json", null, List.of("He left.", "She stayed."), () -> List.of(ITEMS));
    private static final Map<String, SegmentLocator> LOCATORS = Map.of(
            "Book.md:0", new SegmentLocator("ch1 · p01"),
            "Book.md:1", new SegmentLocator("ch1 · p02"));

    private final JobControl control = new JobControl(Set.of());
    private final List<JobEvent> events = new CopyOnWriteArrayList<>();

    @AfterEach
    void clearInterrupt() {
        Thread.interrupted();
    }

    private JobModelCalls callsOver(final ScriptedChatModel model) {
        return new JobModelCalls(
                onSent -> new CancellableChatModel(model, control, onSent),
                events::add,
                new FixedClock(),
                "uk",
                ContextBudget.DEFAULT_WINDOW,
                LOCATORS);
    }

    private List<CallSnapshot> snapshots() {
        return events.stream()
                .filter(CallSnapshotUpdated.class::isInstance)
                .map(event -> ((CallSnapshotUpdated) event).snapshot())
                .toList();
    }

    @Test
    void callAbout_describedAndAnswered_showsOneCallWaitingThenAnsweredWithItsReply() {
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(Result.ok(new ChatResponse("{\"items\":[]}", FinishReason.STOP)));

        callsOver(model).callAbout(CallKind.DRAFT, IDS, REQUEST, BATCH);

        assertThat(snapshots())
                .extracting(CallSnapshot::callId, CallSnapshot::state, CallSnapshot::reply)
                .containsExactly(tuple(1L, CallState.WAITING, null), tuple(1L, CallState.ANSWERED, "{\"items\":[]}"));
        assertThat(snapshots().getLast().usage()).isNotNull();
    }

    @Test
    void callAbout_described_namesEverySegmentWithItsLocatorAndTheSectionsSent() {
        callsOver(TranslationJobTestSupport.replies("x")).callAbout(CallKind.DRAFT, IDS, REQUEST, BATCH);

        final CallSnapshot waiting = snapshots().getFirst();
        assertThat(waiting.segments())
                .containsExactly(
                        new CallSegment("Book.md:0", "ch1 · p01", "He left."),
                        new CallSegment("Book.md:1", "ch1 · p02", "She stayed."));
        assertThat(waiting.sections()).containsExactly(ITEMS);
        assertThat(waiting.label()).isEqualTo("draft-batch-json");
        assertThat(waiting.kind()).isEqualTo(CallKind.DRAFT);
    }

    @Test
    void callAbout_answeredWithAnError_endsTheSnapshotFailed() {
        final ScriptedChatModel failing = new ScriptedChatModel()
                .answer(Result.err(AppError.of(ErrorCode.unreachable, "Offline", "The model is unreachable.")));

        callsOver(failing).callAbout(CallKind.DRAFT, IDS, REQUEST, BATCH);

        assertThat(snapshots()).extracting(CallSnapshot::state).containsExactly(CallState.WAITING, CallState.FAILED);
    }

    @Test
    void callAbout_cancelledInFlight_endsTheSnapshotCancelled() {
        final ScriptedChatModel cancelled =
                new ScriptedChatModel().answer(Result.err(AppError.of(ErrorCode.cancelled, "Cancelled", "Stopped.")));

        callsOver(cancelled).callAbout(CallKind.REVIEW, IDS, REQUEST, BATCH);

        assertThat(snapshots()).extracting(CallSnapshot::state).containsExactly(CallState.WAITING, CallState.CANCELLED);
    }

    // A refused call sends nothing, so there is nothing to show.
    @Test
    void callAbout_pauseRequested_showsNoSnapshot() {
        control.pause();

        callsOver(TranslationJobTestSupport.replies("x")).callAbout(CallKind.DRAFT, IDS, REQUEST, BATCH);

        assertThat(snapshots()).isEmpty();
    }

    @Test
    void callAbout_notDescribed_showsNoSnapshot() {
        callsOver(TranslationJobTestSupport.replies("x")).callAbout(CallKind.SUMMARY, List.of(), REQUEST);

        assertThat(snapshots()).isEmpty();
    }

    @Test
    void callAbout_secondDescribedCall_isNumberedTwo() {
        final JobModelCalls calls = callsOver(TranslationJobTestSupport.replies("a", "b"));

        calls.callAbout(CallKind.DRAFT, IDS, REQUEST, BATCH);
        calls.callAbout(CallKind.REVIEW, IDS, REQUEST, BATCH);

        assertThat(snapshots()).extracting(CallSnapshot::callId).containsExactly(1L, 1L, 2L, 2L);
    }

    // The batch item adopted, then the segment accepted: both notes land on the call that drafted it.
    @Test
    void noted_segmentOfAnAnsweredDraft_showsTheCallAgainWithEachNote() {
        final JobModelCalls calls = callsOver(TranslationJobTestSupport.replies("x"));
        calls.callAbout(CallKind.DRAFT, IDS, REQUEST, BATCH);
        final SegmentOutcomeNote adopted = new SegmentOutcomeNote("Book.md:1", SegmentOutcomeNote.Kind.ADOPTED, "");
        final SegmentOutcomeNote accepted = new SegmentOutcomeNote("Book.md:1", SegmentOutcomeNote.Kind.ACCEPTED, "");

        calls.noted(adopted);
        calls.noted(accepted);

        assertThat(snapshots()).hasSize(4);
        assertThat(snapshots().getLast().outcomes()).containsExactly(adopted, accepted);
        assertThat(snapshots().getLast().callId()).isEqualTo(1L);
    }

    // A decision ends the segment's story: a later note about it belongs to no call this run still shows.
    @Test
    void noted_afterTheSegmentWasDecided_showsNothingMore() {
        final JobModelCalls calls = callsOver(TranslationJobTestSupport.replies("x"));
        calls.callAbout(CallKind.DRAFT, IDS, REQUEST, BATCH);
        calls.noted(new SegmentOutcomeNote("Book.md:0", SegmentOutcomeNote.Kind.FLAGGED, "MISSING"));

        calls.noted(new SegmentOutcomeNote("Book.md:0", SegmentOutcomeNote.Kind.ACCEPTED, ""));

        assertThat(snapshots()).hasSize(3);
    }

    @Test
    void noted_segmentNoCallDrafted_showsNothing() {
        callsOver(TranslationJobTestSupport.replies("x"))
                .noted(new SegmentOutcomeNote("Book.md:9", SegmentOutcomeNote.Kind.ACCEPTED, ""));

        assertThat(snapshots()).isEmpty();
    }

    private static final class FixedClock extends Clock {

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(final ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.parse("2026-10-08T10:00:00Z");
        }
    }
}

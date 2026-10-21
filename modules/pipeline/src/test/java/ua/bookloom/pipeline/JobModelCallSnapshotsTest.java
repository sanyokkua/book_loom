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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.CallItem;
import ua.bookloom.api.pipeline.CallItemStatus;
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
import ua.bookloom.pipeline.prompt.PromptName;
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

    // IF the snapshot held only parts, THEN the person could not see the numbered rules and the checklist that were
    // sent.
    @Test
    void callAbout_described_holdsTheRenderedSystemAndUserMessagesOfTheRequest() {
        final ChatRequest request = new ChatRequest(List.of(
                new ChatMessage(ChatRole.SYSTEM, "Rules:\n1. Translate everything.\n2. Keep names."),
                new ChatMessage(ChatRole.USER, "Translate from English to Ukrainian.\n<s id=\"1\">He left.</s>")));

        callsOver(TranslationJobTestSupport.replies("x")).callAbout(CallKind.DRAFT, IDS, request, BATCH);

        assertThat(snapshots())
                .hasSize(2)
                .allSatisfy(snapshot -> assertThat(snapshot.sent())
                        .containsExactly(
                                new ChatMessage(ChatRole.SYSTEM, "Rules:\n1. Translate everything.\n2. Keep names."),
                                new ChatMessage(
                                        ChatRole.USER,
                                        "Translate from English to Ukrainian.\n<s id=\"1\">He left.</s>")));
    }

    // These are the calls of the glossary scan, the term review, the summary, the garbled-word check and the setup
    // proposals: none names a segment, and each must still appear in the panel.
    @ParameterizedTest
    @EnumSource(
            value = PromptName.class,
            names = {
                "PRESCAN",
                "REVIEW_TERMS",
                "SUGGEST_TARGETS",
                "TERM_CHOICE",
                "SUMMARY",
                "SUSPICIOUS_WORDS",
                "BRIEF_SUGGESTION",
                "FILE_NAME",
                "BOOK_KNOWLEDGE",
                "BRIEF_SYNTHESIS",
                "TERM_READING",
                "LEXICON_READING"
            })
    void callAbout_segmentlessWholePrompt_publishesASnapshotLabelledByItsPrompt(final PromptName name) {
        callsOver(TranslationJobTestSupport.replies("x"))
                .callAbout(name.callKind(), List.of(), REQUEST, CallDescriptor.whole(name));

        assertThat(snapshots())
                .extracting(CallSnapshot::label, CallSnapshot::kind, CallSnapshot::state)
                .containsExactly(
                        tuple(name.resourceBaseName(), name.callKind(), CallState.WAITING),
                        tuple(name.resourceBaseName(), name.callKind(), CallState.ANSWERED));
        assertThat(snapshots().getLast().segments()).isEmpty();
        assertThat(snapshots().getLast().sections()).isEmpty();
        assertThat(snapshots().getLast().sent()).isEqualTo(REQUEST.messages());
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

    private static CallItem item(final String id, final String segmentId, final CallItemStatus status) {
        return new CallItem(id, segmentId, status == CallItemStatus.MISSING ? null : "Target " + id, status, List.of());
    }

    private static List<CallItem> fourItemsOneMissing() {
        return List.of(
                item("1", "Book.md:0", CallItemStatus.OK),
                item("2", "Book.md:1", CallItemStatus.MISSING),
                item("3", "Book.md:2", CallItemStatus.OK),
                item("4", "Book.md:3", CallItemStatus.OK));
    }

    private static final List<String> FOUR_IDS = List.of("Book.md:0", "Book.md:1", "Book.md:2", "Book.md:3");

    // IF a batch reply is parsed, THEN the call it answered is shown again with one item per id, the missing one
    // marked.
    @Test
    void parsed_fourItemBatchOneMissing_snapshotCarriesFourItemsOneMissing() {
        final JobModelCalls calls = callsOver(TranslationJobTestSupport.replies("x"));
        calls.callAbout(CallKind.DRAFT, FOUR_IDS, REQUEST, BATCH);

        calls.parsed(FOUR_IDS, fourItemsOneMissing());

        assertThat(snapshots()).hasSize(3);
        final CallSnapshot shown = snapshots().getLast();
        assertThat(shown.callId()).isEqualTo(1L);
        assertThat(shown.state()).isEqualTo(CallState.ANSWERED);
        assertThat(shown.reply()).isEqualTo("{\"target\":\"x\"}");
        assertThat(shown.items()).hasSize(4);
        assertThat(shown.items())
                .filteredOn(item -> item.status() == CallItemStatus.MISSING)
                .extracting(CallItem::segmentId, CallItem::target)
                .containsExactly(tuple("Book.md:1", null));
        assertThat(shown.segments()).hasSize(4);
    }

    // The batch's outcome notes after the items still land on the same call, and the items stay on it.
    @Test
    void parsed_laterNote_keepsTheItems() {
        final JobModelCalls calls = callsOver(TranslationJobTestSupport.replies("x"));
        calls.callAbout(CallKind.DRAFT, FOUR_IDS, REQUEST, BATCH);
        calls.parsed(FOUR_IDS, fourItemsOneMissing());

        calls.noted(new SegmentOutcomeNote("Book.md:0", SegmentOutcomeNote.Kind.ADOPTED, ""));

        assertThat(snapshots().getLast().items()).hasSize(4);
        assertThat(snapshots().getLast().outcomes()).hasSize(1);
    }

    @Test
    void parsed_afterTheSegmentWasDecided_showsNothingMore() {
        final JobModelCalls calls = callsOver(TranslationJobTestSupport.replies("x"));
        calls.callAbout(CallKind.DRAFT, IDS, REQUEST, BATCH);
        calls.noted(new SegmentOutcomeNote("Book.md:0", SegmentOutcomeNote.Kind.FLAGGED, "MISSING"));

        calls.parsed(List.of("Book.md:0"), List.of(item("1", "Book.md:0", CallItemStatus.OK)));

        assertThat(snapshots()).hasSize(3);
    }

    @Test
    void parsed_noCallDrafted_showsNothing() {
        callsOver(TranslationJobTestSupport.replies("x"))
                .parsed(List.of("Book.md:9"), List.of(item("1", "Book.md:9", CallItemStatus.OK)));

        assertThat(snapshots()).isEmpty();
    }

    @Test
    void parsed_noSegmentIds_showsNothing() {
        callsOver(TranslationJobTestSupport.replies("x")).parsed(List.of(), List.of());

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

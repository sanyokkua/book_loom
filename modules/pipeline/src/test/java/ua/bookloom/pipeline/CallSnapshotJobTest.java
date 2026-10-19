package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static ua.bookloom.pipeline.BatchedJobs.batchedJob;
import static ua.bookloom.pipeline.ChunkRunFixtures.S0;
import static ua.bookloom.pipeline.ChunkRunFixtures.S1;
import static ua.bookloom.pipeline.ChunkRunFixtures.S2;
import static ua.bookloom.pipeline.ChunkRunFixtures.S3;
import static ua.bookloom.pipeline.ChunkRunFixtures.T0;
import static ua.bookloom.pipeline.ChunkRunFixtures.T1;
import static ua.bookloom.pipeline.ChunkRunFixtures.T2;
import static ua.bookloom.pipeline.ChunkRunFixtures.T3;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;
import static ua.bookloom.pipeline.TranslationJobTestSupport.replies;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.CallSegment;
import ua.bookloom.api.pipeline.CallSnapshot;
import ua.bookloom.api.pipeline.CallSnapshotUpdated;
import ua.bookloom.api.pipeline.CallState;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.PromptSection;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.SegmentOutcomeNote;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;

/**
 * A run shows each model call as the call it really was: a batch call about every segment it drafted, with its prompt
 * part by part, its reply and what became of each segment; a reviewer call about its chunk. The model is scripted at
 * the call seam.
 */
class CallSnapshotJobTest {

    private static final String BATCH = "draft-batch-json";
    private static final List<String> SOURCES = List.of(
            S0,
            S1,
            S2,
            S3,
            "The gulls cried above the pier.",
            "A lamp burned in the window.",
            "The fisherman mended his nets.",
            "Night fell over the quiet town.");
    private static final List<String> TARGETS = List.of(
            T0,
            T1,
            T2,
            T3,
            "Чайки кричали над причалом.",
            "У вікні горіла лампа.",
            "Рибалка лагодив свої сіті.",
            "Ніч опустилася на тихе містечко.");

    @TempDir
    private Path tempDir;

    private final List<JobEvent> events = new CopyOnWriteArrayList<>();

    @AfterEach
    void cleanUpWorkers() {
        TranslationJobTestSupport.shutdownAll();
    }

    private static Result<ChatResponse> raw(final String json) {
        return Result.ok(new ChatResponse(json, FinishReason.STOP));
    }

    private static String items(final int... skipped) {
        final List<String> entries = new ArrayList<>();
        for (int index = 0; index < TARGETS.size(); index++) {
            final int id = index + 1;
            if (Arrays.stream(skipped).noneMatch(skip -> skip == id)) {
                entries.add("{\"id\":\"" + id + "\",\"target\":\"" + TARGETS.get(index) + "\"}");
            }
        }
        return "{\"items\":[" + String.join(",", entries) + "]}";
    }

    private TestProject eightParagraphs(final QualityDial dial) {
        final Path book = TestBooks.markdown(tempDir.resolve("Book.md"), String.join("\n\n", SOURCES));
        return project(book, brief("en", "uk", dial));
    }

    private List<CallSnapshot> snapshots(final CallKind kind) {
        return events.stream()
                .filter(CallSnapshotUpdated.class::isInstance)
                .map(event -> ((CallSnapshotUpdated) event).snapshot())
                .filter(snapshot -> snapshot.kind() == kind)
                .toList();
    }

    private void run(final TestProject project, final ScriptedChatModel model) {
        final TranslationJobImpl job = batchedJob(project, model);
        job.subscribe(events::add);
        report(job.run());
    }

    @Test
    void run_batchOfEight_showsOneCallWaitingThenAnsweredAboutAllEightWithTheReply() {
        final String reply = items();
        final ScriptedChatModel model = new ScriptedChatModel().answerTo(BATCH, raw(reply));

        run(eightParagraphs(QualityDial.FAST), model);

        final List<CallSnapshot> drafts = snapshots(CallKind.DRAFT);
        assertThat(drafts.subList(0, 2))
                .extracting(CallSnapshot::callId, CallSnapshot::state, CallSnapshot::reply, CallSnapshot::label)
                .containsExactly(
                        tuple(1L, CallState.WAITING, null, BATCH), tuple(1L, CallState.ANSWERED, reply, BATCH));
        assertThat(drafts.getFirst().segments()).hasSize(8);
        assertThat(drafts.getFirst().segments().getFirst()).isEqualTo(new CallSegment("Book.md:0", "ch1 · p01", S0));
        assertThat(drafts.getFirst().position()).isNotNull();
    }

    // The system message's parts come first, in its template's order, then the user message's; the first batch of a
    // book has no earlier pairs and no next item, so only the items are sent in the user message.
    @Test
    void run_batchOfEight_showsTheSectionsInTheOrderThePromptSentThem() {
        run(eightParagraphs(QualityDial.FAST), new ScriptedChatModel().answerTo(BATCH, raw(items())));

        final List<PromptSection> sections =
                snapshots(CallKind.DRAFT).getFirst().sections();
        assertThat(sections)
                .extracting(PromptSection::slot, PromptSection::origin)
                .containsExactly(
                        tuple("styleSheet", PromptSection.Origin.SYSTEM),
                        tuple("languageRules", PromptSection.Origin.SYSTEM),
                        tuple("examples", PromptSection.Origin.SYSTEM),
                        tuple("items", PromptSection.Origin.USER));
        assertThat(sections.getLast().lines()).hasSize(8).first().isEqualTo("<s id=\"1\">" + S0 + "</s>");
    }

    @Test
    void run_batchOfEight_notesEachAdoptionThenEachAcceptanceOnTheBatchCall() {
        run(eightParagraphs(QualityDial.FAST), new ScriptedChatModel().answerTo(BATCH, raw(items())));

        final List<SegmentOutcomeNote> notes =
                snapshots(CallKind.DRAFT).getLast().outcomes();
        assertThat(notes).extracting(SegmentOutcomeNote::kind).hasSize(16);
        assertThat(notes.subList(0, 8)).allMatch(note -> note.kind() == SegmentOutcomeNote.Kind.ADOPTED);
        assertThat(notes.subList(8, 16)).allMatch(note -> note.kind() == SegmentOutcomeNote.Kind.ACCEPTED);
    }

    // The item the reply left out falls back to its own draft call, which then receives its decision.
    @Test
    void run_replyMissingOneId_notesTheFallbackOnTheBatchAndTheDecisionOnTheSingleDraft() {
        final ScriptedChatModel model = replies(TARGETS.get(1)).answerTo(BATCH, raw(items(2)));

        run(eightParagraphs(QualityDial.FAST), model);

        assertThat(lastOf(1L).outcomes())
                .filteredOn(note -> note.segmentId().equals("Book.md:1"))
                .extracting(SegmentOutcomeNote::kind, SegmentOutcomeNote::detail)
                .containsExactly(tuple(SegmentOutcomeNote.Kind.FELL_BACK, "MISSING"));
        assertThat(lastOf(2L))
                .extracting(CallSnapshot::label, CallSnapshot::segments)
                .containsExactly("draft", List.of(new CallSegment("Book.md:1", "ch1 · p02", S1)));
        assertThat(lastOf(2L).outcomes())
                .extracting(SegmentOutcomeNote::segmentId, SegmentOutcomeNote::kind)
                .containsExactly(tuple("Book.md:1", SegmentOutcomeNote.Kind.ACCEPTED));
    }

    private CallSnapshot lastOf(final long callId) {
        return snapshots(CallKind.DRAFT).stream()
                .filter(snapshot -> snapshot.callId() == callId)
                .toList()
                .getLast();
    }

    @Test
    void run_reviewedChunk_showsTheReviewerCallAboutTheChunkWithItsOwnSections() {
        final ScriptedChatModel model =
                new ScriptedChatModel().answerTo(BATCH, raw(items())).answerTo("reviewer", raw("{\"results\":[]}"));

        run(eightParagraphs(QualityDial.BALANCED), model);

        final List<CallSnapshot> reviews = snapshots(CallKind.REVIEW);
        assertThat(reviews)
                .extracting(CallSnapshot::state, CallSnapshot::label)
                .containsExactly(tuple(CallState.WAITING, "reviewer"), tuple(CallState.ANSWERED, "reviewer"));
        assertThat(reviews.getFirst().callId()).isEqualTo(2L);
        assertThat(reviews.getFirst().segments()).hasSize(8);
        assertThat(reviews.getFirst().sections())
                .extracting(PromptSection::slot)
                .startsWith("styleSheet")
                .endsWith("pairs");
    }
}

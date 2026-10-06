package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.pipeline.eval.RealRunRow.Outcome;
import ua.bookloom.pipeline.prompt.ModelCalls;

/** The real-run runner against a scripted model: the requests it sends are the run's, and every reply is measured. */
class RealRunRunnerTest {

    private static final String DRAFT_REPLY = "{\"target\":\"«Зачекай», — сказав хлопчик, — «лампа згасла».\"}";
    private static final String REVIEW_OK = "{\"results\":[{\"id\":\"s1\",\"status\":\"ok\"}]}";

    @TempDir
    private Path dir;

    private record Sent(CallKind kind, ChatRequest request) {}

    private static ModelCalls scripted(final List<Sent> sent, final Function<CallKind, ChatResponse> replies) {
        return (kind, segmentId, request) -> {
            sent.add(new Sent(kind, request));
            return Result.ok(replies.apply(kind));
        };
    }

    private static ChatResponse stop(final String content) {
        return new ChatResponse(content, FinishReason.STOP);
    }

    private static RunCase text(final String id) {
        return RunCorpus.text().stream()
                .filter(c -> c.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private static String all(final Sent sent) {
        return sent.request().messages().stream()
                .map(message -> message.content())
                .reduce("", (a, b) -> a + "\n" + b);
    }

    private RealRunRunner runner(final List<Sent> sent, final Function<CallKind, ChatResponse> replies) {
        return new RealRunRunner(scripted(sent, replies), 1, dir);
    }

    @Test
    void text_quotesDraft_carriesTheUkrainianRulesQuoteInstruction() {
        final List<Sent> sent = new ArrayList<>();

        runner(sent, kind -> stop(kind == CallKind.DRAFT ? DRAFT_REPLY : REVIEW_OK))
                .text(text("quotes-stray-close-1"));

        assertThat(sent.getFirst().kind()).isEqualTo(CallKind.DRAFT);
        assertThat(all(sent.getFirst())).contains("«…» for speech, „…“ inside; never “…”.");
    }

    @Test
    void text_maleNarratorDraft_carriesTheNarratorLineInTheStyleSheet() {
        final List<Sent> sent = new ArrayList<>();

        runner(sent, kind -> stop(DRAFT_REPLY)).text(text("narrator-could-not"));

        assertThat(all(sent.getFirst())).contains("Narrator: first person, male");
    }

    @Test
    void text_femaleNarratorDraft_carriesTheNarratorLineInTheStyleSheet() {
        final List<Sent> sent = new ArrayList<>();

        runner(sent, kind -> stop(DRAFT_REPLY)).text(text("narrator-female-slip"));

        assertThat(all(sent.getFirst())).contains("Narrator: first person, female");
    }

    @Test
    void text_defectiveQuotes_areCaughtByTheGateSoOnlyTheCleanCandidateIsReviewed() {
        final List<Sent> sent = new ArrayList<>();

        final List<RealRunRow> rows = runner(sent, kind -> stop(kind == CallKind.DRAFT ? DRAFT_REPLY : REVIEW_OK))
                .text(text("quotes-stray-close-1"));

        assertThat(sent).extracting(Sent::kind).containsExactly(CallKind.DRAFT, CallKind.REVIEW);
        assertThat(rows).extracting(RealRunRow::call).containsExactly("draft", "review");
        assertThat(rows).extracting(RealRunRow::outcome).containsOnly(Outcome.PASS);
    }

    @Test
    void text_draftWithUnbalancedQuotes_failsTheDraftRow() {
        final List<Sent> sent = new ArrayList<>();

        final List<RealRunRow> rows = runner(
                        sent, kind -> stop("{\"target\":\"Зачекай», — сказав хлопчик, — «лампа згасла».\"}"))
                .text(text("quotes-stray-close-1"));

        assertThat(rows.getFirst().outcome()).isEqualTo(Outcome.FAIL);
    }

    @Test
    void text_shortLine_isAKnownFailureTheGateRefusesBeforeAnyReviewer() {
        final List<Sent> sent = new ArrayList<>();

        final List<RealRunRow> rows = runner(sent, kind -> stop("{\"target\":\"А за мить — вибух.\"}"))
                .text(text("short-split-second"));

        assertThat(sent).extracting(Sent::kind).containsExactly(CallKind.DRAFT);
        assertThat(rows).extracting(RealRunRow::outcome).containsOnly(Outcome.KNOWN_RED);
    }

    @Test
    void text_russianLetterCase_isOnlyAReviewerRowUntilTheAlphabetCheckLands() {
        final List<Sent> sent = new ArrayList<>();

        final List<RealRunRow> rows = runner(sent, kind -> stop(REVIEW_OK)).text(text("russian-mare"));

        assertThat(rows).extracting(RealRunRow::call).containsExactly("review");
        assertThat(rows.getFirst().outcome()).isEqualTo(Outcome.FAIL);
    }

    @Test
    void reviewerBatch_oneDefectFlaggedAmongCleanPairs_everyPairRowPasses() {
        final ReviewerBatchCase batchCase = RunCorpus.reviewerBatches().getFirst();
        final String reply = "{\"results\":["
                + "{\"id\":\"s1\",\"status\":\"ok\"},{\"id\":\"s2\",\"status\":\"ok\"},{\"id\":\"s3\",\"status\":\"ok\"},"
                + "{\"id\":\"s4\",\"status\":\"edits\",\"edits\":[{\"criterion\":\"terminology\","
                + "\"quote\":\"Ніл\",\"replacement\":\"Нелл\"}]},"
                + "{\"id\":\"s5\",\"status\":\"ok\"},{\"id\":\"s6\",\"status\":\"ok\"}]}";
        final List<Sent> sent = new ArrayList<>();

        final List<RealRunRow> rows = runner(sent, kind -> stop(reply)).reviewerBatch(batchCase);

        assertThat(sent).hasSize(1);
        assertThat(all(sent.getFirst())).contains("Нелл", "Nell → Нелл");
        assertThat(rows)
                .as(rows.toString())
                .hasSize(6)
                .extracting(RealRunRow::outcome)
                .containsOnly(Outcome.PASS);
        assertThat(rows).extracting(RealRunRow::truncated).containsOnly(false);
    }

    @Test
    void reviewerBatch_replyCutByTheCap_isCountedOnceAsATruncatedCall() {
        final ReviewerBatchCase longCase = RunCorpus.reviewerBatches().stream()
                .filter(c -> c.kind().equals("reviewer-long"))
                .findFirst()
                .orElseThrow();
        final List<Sent> sent = new ArrayList<>();

        final List<RealRunRow> rows = runner(
                        sent,
                        kind -> new ChatResponse(
                                "{\"results\":[{\"id\":\"s1\",\"status\":\"ok\"},{\"id\":\"s2\",\"sta",
                                FinishReason.LENGTH))
                .reviewerBatch(longCase);

        assertThat(sent).hasSize(1);
        assertThat(all(sent.getFirst())).contains("s12");
        assertThat(rows).hasSize(12);
        assertThat(rows.stream().filter(RealRunRow::truncated)).hasSize(1);
        assertThat(rows.getFirst().truncated()).isTrue();
    }

    @Test
    void reviewerBatch_repeatedCallsThatDisagree_areMarkedUnstable() {
        final ReviewerBatchCase batchCase = RunCorpus.reviewerBatches().get(1);
        final List<String> replies = new ArrayList<>(List.of(
                "{\"results\":[{\"id\":\"s3\",\"status\":\"edits\",\"edits\":[{\"criterion\":\"terminology\","
                        + "\"quote\":\"Майстер\",\"replacement\":\"Господар\"}]}]}",
                "{\"results\":[]}"));
        final ModelCalls calls = (kind, segmentId, request) -> Result.ok(stop(replies.removeFirst()));

        final List<RealRunRow> rows = new RealRunRunner(calls, 2, dir).reviewerBatch(batchCase);

        assertThat(rows.get(2).stable()).isFalse();
        assertThat(rows.get(0).stable()).isTrue();
    }

    @Test
    void batch_correctReply_passesWithNoTooShortOrLeakedItem() {
        final BatchCase batchCase = RunCorpus.batches().getFirst();
        final RealRunRow row = runner(
                        new ArrayList<>(),
                        kind -> stop(batchCase.replies().getFirst().reply()))
                .batch(batchCase);

        assertThat(row.outcome()).isEqualTo(Outcome.PASS);
        assertThat(row.items()).isEqualTo(4);
        assertThat(row.tooShort()).isZero();
        assertThat(row.leaked()).isZero();
        assertThat(row.detail()).contains("termsReported=2");
    }

    @Test
    void batch_leakedTermsReply_failsWithTheLeakCounted() {
        final BatchCase batchCase = RunCorpus.batches().getFirst();
        final String leak = reply(batchCase, "TERMS_LEAK");

        final RealRunRow row = runner(new ArrayList<>(), kind -> stop(leak)).batch(batchCase);

        assertThat(row.outcome()).isEqualTo(Outcome.FAIL);
        assertThat(row.leaked()).isEqualTo(1);
    }

    @Test
    void batch_codeFenceReply_failsWithTheLeakCounted() {
        final BatchCase batchCase = RunCorpus.batches().getFirst();

        final RealRunRow row = runner(new ArrayList<>(), kind -> stop(reply(batchCase, "CODE_FENCE")))
                .batch(batchCase);

        assertThat(row.leaked()).isEqualTo(1);
    }

    @Test
    void batch_tooShortReply_countsTheShortItem() {
        final BatchCase batchCase = RunCorpus.batches().getFirst();

        final RealRunRow row = runner(new ArrayList<>(), kind -> stop(reply(batchCase, "TOO_SHORT")))
                .batch(batchCase);

        assertThat(row.outcome()).isEqualTo(Outcome.FAIL);
        assertThat(row.tooShort()).isEqualTo(1);
    }

    @Test
    void repair_placeholderCase_sendsTheRunsRepairRequestAndTheGateReadsTheAnswer() {
        final RepairCase repairCase = RunCorpus.repairs().getFirst();
        final List<Sent> sent = new ArrayList<>();
        final String answer = "{\"target\":\"" + repairCase.good() + "\"}";

        final RealRunRow row = runner(sent, kind -> stop(answer)).repair(repairCase);

        assertThat(sent).singleElement().satisfies(call -> {
            assertThat(call.kind()).isEqualTo(CallKind.PLACEHOLDER_REPAIR);
            assertThat(all(call)).contains(repairCase.rejected());
        });
        assertThat(row.outcome()).isEqualTo(Outcome.PASS);
    }

    @Test
    void repair_answerThatStillBreaksThePlaceholders_failsTheDocumentGate() {
        final RepairCase repairCase = RunCorpus.repairs().getFirst();
        final String answer = "{\"target\":\"" + repairCase.bad() + "\"}";

        final RealRunRow row = runner(new ArrayList<>(), kind -> stop(answer)).repair(repairCase);

        assertThat(row.outcome()).isEqualTo(Outcome.FAIL);
    }

    @Test
    void repair_structuralCase_sendsAStructuralRepairCall() {
        final RepairCase repairCase = RunCorpus.repairs().stream()
                .filter(c -> c.step() == ua.bookloom.pipeline.eval.EvalCase.RepairStep.STRUCTURAL)
                .findFirst()
                .orElseThrow();
        final List<Sent> sent = new ArrayList<>();

        final RealRunRow row = runner(sent, kind -> stop("{\"target\":\"" + repairCase.good() + "\"}"))
                .repair(repairCase);

        assertThat(sent).extracting(Sent::kind).containsExactly(CallKind.STRUCTURAL_REPAIR);
        assertThat(row.outcome()).isEqualTo(Outcome.PASS);
    }

    private static String reply(final BatchCase batchCase, final String shape) {
        return batchCase.replies().stream()
                .filter(reply -> reply.shape().equals(shape))
                .findFirst()
                .orElseThrow()
                .reply();
    }
}

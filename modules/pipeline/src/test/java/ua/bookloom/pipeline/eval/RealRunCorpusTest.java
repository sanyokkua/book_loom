package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.pipeline.batch.BatchItem;
import ua.bookloom.pipeline.batch.BatchReply;
import ua.bookloom.pipeline.batch.BatchReplyParser;
import ua.bookloom.pipeline.eval.RunCase.Check;
import ua.bookloom.pipeline.glossary.FrequencyScan;
import ua.bookloom.pipeline.glossary.KeyTermScan;
import ua.bookloom.pipeline.glossary.NameCandidate;
import ua.bookloom.pipeline.run.PromptRequests.PreparedBatch;

/**
 * The real-run corpus (15e.2) held to production code with no model: every label of a deterministic case is what the
 * production checks say, the known failures are still wrong, and the batch replies are read by the production parser.
 */
class RealRunCorpusTest {

    private static Stream<Arguments> named(final Stream<RunCase> cases) {
        return cases.map(runCase -> Arguments.of(runCase.id(), runCase));
    }

    private static Stream<Arguments> decided() {
        return named(RunCorpus.text().stream().filter(c -> c.isDeterministic() && !c.knownFailure()));
    }

    private static Stream<Arguments> decidedDefects() {
        return named(
                RunCorpus.text().stream().filter(c -> c.isDeterministic() && !c.knownFailure() && c.bad() != null));
    }

    private static Stream<Arguments> quoteAndScriptDefects() {
        return named(RunCorpus.text().stream()
                .filter(c -> c.check() == Check.QUOTES || c.check() == Check.SCRIPT)
                .filter(c -> c.bad() != null));
    }

    private static Stream<Arguments> reviewerOnlyDefects() {
        return named(RunCorpus.text().stream().filter(c -> !c.isDeterministic() && c.bad() != null));
    }

    private static Stream<Arguments> batchCases() {
        return RunCorpus.batches().stream().map(c -> Arguments.of(c.id(), c));
    }

    private static Stream<Arguments> batchReplies(final boolean known) {
        return RunCorpus.batches().stream()
                .flatMap(c -> c.replies().stream()
                        .filter(reply -> reply.knownFailure() == known)
                        .map(reply -> Arguments.of(c.id() + "/" + reply.shape(), c, reply)));
    }

    private static Stream<Arguments> acceptedBatchReplies() {
        return batchReplies(false);
    }

    private static Stream<Arguments> scans(final boolean known) {
        return RunCorpus.scans().stream().filter(c -> c.knownFailure() == known).map(c -> Arguments.of(c.id(), c));
    }

    private static Stream<Arguments> rightScans() {
        return scans(false);
    }

    private static Stream<Arguments> knownScans() {
        return scans(true);
    }

    private static List<String> proposed(final ScanCase scanCase) {
        final List<ua.bookloom.api.document.Segment> segments = new java.util.ArrayList<>();
        for (int round = 0; round < scanCase.repeat(); round++) {
            scanCase.lines().forEach(line -> segments.add(EvalProject.segment(segments.size(), line)));
        }
        return (scanCase.scan() == ScanCase.Scan.KEY_TERMS
                        ? KeyTermScan.candidates(segments, "en")
                        : FrequencyScan.candidates(segments, 1, "en"))
                .stream().map(NameCandidate::term).toList();
    }

    private static Stream<Arguments> repairs() {
        return RunCorpus.repairs().stream().map(c -> Arguments.of(c.id(), c));
    }

    @Test
    void corpus_text_hasTheKindsTheRealRunShowed() {
        final Map<String, Long> kinds =
                RunCorpus.text().stream().collect(Collectors.groupingBy(RunCase::kind, Collectors.counting()));

        assertThat(kinds)
                .containsEntry("quotes", 10L)
                .containsEntry("mixed-script", 6L)
                .containsEntry("invented-word", 3L)
                .containsEntry("russian-letters", 5L)
                .containsEntry("narrator", 6L)
                .containsEntry("short-line", 6L);
    }

    @Test
    void corpus_ids_areUniqueAcrossEveryFile() {
        final List<String> ids = Stream.of(
                        RunCorpus.text().stream().map(RunCase::id),
                        RunCorpus.batches().stream().map(BatchCase::id),
                        RunCorpus.reviewerBatches().stream().map(ReviewerBatchCase::id),
                        RunCorpus.repairs().stream().map(RepairCase::id))
                .flatMap(Function.identity())
                .toList();

        assertThat(ids).doesNotHaveDuplicates();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("decided")
    void verdict_cleanCandidate_productionCheckDoesNotFire(final String id, final RunCase runCase) {
        assertThat(RunVerdicts.of(runCase).fires(runCase.good())).as(id).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("decidedDefects")
    void verdict_defectiveCandidate_productionCheckFires(final String id, final RunCase runCase) {
        assertThat(RunVerdicts.of(runCase).fires(runCase.bad())).as(id).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("quoteAndScriptDefects")
    void verdict_quoteOrScriptDefect_theRunRefusesItBeforeAnyReviewer(final String id, final RunCase runCase) {
        final RunVerdicts verdicts = RunVerdicts.of(runCase);

        assertThat(verdicts.refuses(runCase.bad())).as(id + " bad").isTrue();
        assertThat(verdicts.refuses(runCase.good())).as(id + " good").isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("reviewerOnlyDefects")
    void verdict_reviewerOnlyDefect_noTextCheckBlocksIt(final String id, final RunCase runCase) {
        assertThat(RunVerdicts.of(runCase).hasAnyBlockingFinding(runCase.bad()))
                .as(id)
                .isFalse();
    }

    @Test
    void knownFailure_cleanCandidates_areStillRefusedByProduction() {
        assertThat(RunCorpus.text().stream().filter(RunCase::knownFailure))
                .allSatisfy(runCase -> assertThat(RunVerdicts.of(runCase).fires(runCase.good()))
                        .as(runCase.id() + " now passes: drop knownFailure from the case (task " + runCase.fixedBy()
                                + ")")
                        .isTrue());
    }

    @Test
    void knownFailure_everyCase_namesTheTaskThatFixesIt() {
        assertThat(RunCorpus.text().stream().filter(RunCase::knownFailure))
                .allSatisfy(runCase -> assertThat(runCase.fixedBy()).matches("15e\\.\\d+"));
        assertThat(RunCorpus.batches().stream()
                        .flatMap(c -> c.replies().stream())
                        .filter(BatchCase.Reply::knownFailure))
                .allSatisfy(reply -> assertThat(reply.fixedBy()).matches("15e\\.\\d+"));
    }

    @Test
    void shortLines_sizes_sitAtTheLengthThreshold() {
        final List<RunCase> shorts = RunCorpus.text().stream()
                .filter(c -> c.kind().equals("short-line"))
                .toList();

        assertThat(shorts).allSatisfy(c -> {
            assertThat(c.source().length()).isBetween(36, 60);
            assertThat(c.source().split("\\s+")).hasSizeBetween(6, 10);
            assertThat(c.bad()).isNull();
        });
        assertThat(shorts.getFirst().source()).isEqualTo("And, a split second later, the explosion.");
    }

    @Test
    void narratorCases_carryTheNarratorTheBriefSets() {
        assertThat(RunCorpus.text().stream().filter(c -> c.kind().equals("narrator")))
                .hasSize(6)
                .allSatisfy(c -> assertThat(c.surroundings().narrator()).isNotNull());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("batchCases")
    void batch_requestedByTheRun_asksForTermsAndCarriesItsContext(final String id, final BatchCase batchCase) {
        final EvalProject project = EvalProject.of(batchCase.setup("en", "uk"), (kind, segment, request) -> {
            throw new IllegalStateException("no call");
        });
        final PreparedBatch prepared = project.batch().orElseThrow();
        final String user = user(project.batchRequest(prepared));

        assertThat(prepared.items()).hasSize(batchCase.chunk().size());
        assertThat(prepared.context().keyTerms()).isNotEmpty().contains("master");
        assertThat(user)
                .contains("master", "master → господар", "Нелл")
                .contains("Source: "
                        + batchCase.surroundings().preceding().getFirst().source());
    }

    @Test
    void batch_contextCase_carriesTheRollingSummary() {
        final BatchCase batchCase = RunCorpus.batches().stream()
                .filter(c -> c.kind().equals("batch-context"))
                .findFirst()
                .orElseThrow();
        final EvalProject project = EvalProject.of(batchCase.setup("en", "uk"), (kind, segment, request) -> {
            throw new IllegalStateException("no call");
        });

        assertThat(user(project.batchRequest(project.batch().orElseThrow())))
                .contains("Раніше: Нелл і хлопчик прийшли до дому господаря");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("acceptedBatchReplies")
    void batchReply_productionParser_verdictMatchesTheLabel(
            final String id, final BatchCase batchCase, final BatchCase.Reply reply) {
        assertThat(parse(batchCase, reply).isClean()).as(id).isEqualTo(reply.expectAccepted());
    }

    @Test
    void knownFailure_batchReplies_areStillReadWrongByProduction() {
        assertThat(RunCorpus.batches())
                .allSatisfy(batchCase -> assertThat(batchCase.replies().stream().filter(BatchCase.Reply::knownFailure))
                        .allSatisfy(reply -> assertThat(parse(batchCase, reply).isClean())
                                .as(batchCase.id() + "/" + reply.shape() + " now reads right: drop knownFailure (task "
                                        + reply.fixedBy() + ")")
                                .isNotEqualTo(reply.expectAccepted())));
    }

    @Test
    void batchReply_correctShape_reportsTheTermsTheChunkAskedFor() {
        final BatchCase batchCase = RunCorpus.batches().getFirst();
        final BatchCase.Reply correct = batchCase.replies().getFirst();

        assertThat(parse(batchCase, correct).outcome("1").orElseThrow().terms())
                .containsEntry("master", "господар")
                .containsEntry("imp", "імп");
    }

    @Test
    void batchReply_tooShortShape_isMarkedTooShortByTheItemValidator() {
        final BatchCase batchCase = RunCorpus.batches().getFirst();
        final BatchCase.Reply tooShort = batchCase.replies().stream()
                .filter(reply -> reply.shape().equals("TOO_SHORT"))
                .findFirst()
                .orElseThrow();

        assertThat(parse(batchCase, tooShort).outcome("3").orElseThrow().problems())
                .containsExactly(ua.bookloom.pipeline.batch.ItemProblem.TOO_SHORT);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("repairs")
    void repair_productionDocumentGate_acceptsTheGoodAnswerAndRefusesTheBadOne(
            final String id,
            final RepairCase repairCase,
            @org.junit.jupiter.api.io.TempDir final java.nio.file.Path dir) {
        final RealRunRunner runner = new RealRunRunner((kind, segment, request) -> null, 1, dir);
        final ua.bookloom.api.document.Segment real = runner.documentSegment(repairCase);

        assertThat(runner.restores(real, repairCase.good())).as(id + " good").isTrue();
        assertThat(runner.restores(real, repairCase.bad())).as(id + " bad").isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rightScans")
    void scan_productionScan_proposesTheTermsTheCaseNamesAndNoGenericWord(final String id, final ScanCase scanCase) {
        final List<String> proposed = proposed(scanCase);

        assertThat(scanCase.mustPropose().stream().filter(term -> !proposed.contains(term)))
                .as(id + " missing")
                .isEmpty();
        assertThat(proposed.stream().filter(scanCase.mustNotPropose()::contains))
                .as(id + " generic")
                .isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("knownScans")
    void knownFailure_scan_stillProposesAGenericWord(final String id, final ScanCase scanCase) {
        assertThat(proposed(scanCase))
                .as(id + " now proposes no generic word: drop knownFailure (task " + scanCase.fixedBy() + ")")
                .containsAnyElementsOf(scanCase.mustNotPropose());
    }

    private static BatchReply parse(final BatchCase batchCase, final BatchCase.Reply reply) {
        final EvalProject project = EvalProject.of(batchCase.setup("en", "uk"), (kind, segment, request) -> {
            throw new IllegalStateException("no call");
        });
        final List<BatchItem> items = project.batch().orElseThrow().items();
        return new BatchReplyParser(new com.fasterxml.jackson.databind.ObjectMapper())
                .parse(reply.reply(), items, "en", "uk");
    }

    private static String user(final ChatRequest request) {
        return request.messages().get(1).content();
    }
}

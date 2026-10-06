package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.ModelCallFinished;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.pipeline.eval.SequenceMetrics.NameStat;
import ua.bookloom.pipeline.eval.SequenceMetrics.TermStat;
import ua.bookloom.pipeline.eval.SequenceRun.Decided;

/**
 * The metric code of the sequence eval proven without a model: the fixture through the real job against a scripted
 * translator with known drift, and the measure over a run written by hand for the defects the job would repair or flag.
 */
class SequenceEvalOfflineTest {

    private static final SequenceFixture FIXTURE = SequenceFixture.load();
    private static SequenceMetrics scripted;

    @TempDir
    private static Path workDir;

    @BeforeAll
    static void runScriptedBook() {
        final SequenceRun run = new SequenceEval(FIXTURE, QualityDial.FAST, null, SequenceNarratorMode.UNSET)
                .run(new SequenceScriptedModel(), workDir);
        assertThat(run.report().end()).isEqualTo(JobState.COMPLETED);
        scripted = new SequenceMeasure(FIXTURE).measure("scripted", "FAST", run);
    }

    private static TermStat term(final String name) {
        return scripted.terms().stream()
                .filter(t -> t.term().equals(name))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void run_scriptedBook_decidesEverySegmentAndFlagsTheEchoedOneWithNoTarget() {
        assertThat(scripted.segments()).isEqualTo(328);
        assertThat(scripted.flagged()).isEqualTo(1);
        assertThat(scripted.flaggedWithoutTarget()).isEqualTo(1);
        assertThat(scripted.flaggedRate()).isCloseTo(1.0 / 328, within(0.0001));
        assertThat(scripted.narrator()).isEqualTo("unset");
    }

    @Test
    void run_masterDriftsHalfway_twoRenderingsWithTheirCounts() {
        assertThat(term("master").segments()).isEqualTo(59);
        assertThat(term("master").distinct()).isEqualTo(2);
        assertThat(term("master").renderings()).containsEntry("господар", 27).containsEntry("учитель", 22);
        assertThat(term("master").dominantShare()).isCloseTo(27.0 / 49, within(0.0001));
    }

    @Test
    void run_mrDriftsHalfway_twoRenderingsAndTheEchoedParagraphIsNotCounted() {
        assertThat(term("Mr").segments()).isEqualTo(71);
        assertThat(term("Mr").renderings()).containsEntry("містер", 37).containsEntry("пан", 33);
    }

    @Test
    void run_steadyTerms_oneRenderingEach() {
        assertThat(term("imp").distinct()).isEqualTo(1);
        assertThat(term("boy").renderings()).containsEntry("хлопч", 68);
        assertThat(term("boy").dominantShare()).isEqualTo(1.0);
        assertThat(term("Mrs").renderings()).containsEntry("місіс", 64).containsEntry("пані", 0);
    }

    @Test
    void run_twoDriftingTermsOfTen_meanRenderingsPerTermIsSixFifths() {
        assertThat(scripted.renderingsPerTerm()).isCloseTo(1.2, within(0.001));
        assertThat(scripted.dominantShareMean()).isCloseTo(0.9080, within(0.0005));
    }

    @Test
    void run_bartimaeusSpelledTwoWays_oneVariantBeyondTheFirst() {
        final NameStat name = scripted.names().stream()
                .filter(n -> n.name().equals("Bartimaeus"))
                .findFirst()
                .orElseThrow();

        assertThat(name.variants()).isEqualTo(2);
        assertThat(name.spellings()).containsEntry("бартімей", 27).containsEntry("бартімеус", 29);
        assertThat(scripted.nameVariants()).isEqualTo(1);
    }

    @Test
    void run_oneNarratorParagraphSaysYaBula_oneGenderSlip() {
        assertThat(scripted.genderSlips()).isEqualTo(1);
    }

    @Test
    void run_strayQuoteAndMixedQuotesKept_areCountedAsAsciiQuotes() {
        assertThat(scripted.asciiQuotes()).isEqualTo(7);
        assertThat(scripted.quoteFailures()).isZero();
    }

    @Test
    void run_echoedParagraph_isTheOnlyFirstRoundHardGateFailure() {
        assertThat(scripted.hardGateFailuresRound0()).isEqualTo(1);
        assertThat(scripted.hardGateKinds()).containsKey("language");
        assertThat(scripted.leakedProtocol()).isZero();
        assertThat(scripted.reviewerTruncated()).isZero();
        assertThat(scripted.termClaimedWrong()).isZero();
    }

    @Test
    void run_fastDial_callsAreBatchedAndTheEchoFellBackOnce() {
        assertThat(scripted.callsByKind().get("DRAFT")).isPositive();
        assertThat(scripted.batchFallbacks()).isEqualTo(1);
        assertThat(scripted.fallbackReasons()).containsEntry("OK/[ECHO]", 1);
        assertThat(scripted.callsPerSegment()).isLessThan(1.0);
    }

    private static List<Decided> handWrittenSegments() {
        return List.of(
                decided(0, NarratorPerson.THIRD, "The master came.", "Господар прийшов.", SegmentStatus.ACCEPTED),
                decided(1, NarratorPerson.FIRST, "I laughed.", "Я була рада.", SegmentStatus.ACCEPTED),
                decided(2, NarratorPerson.THIRD, "The master left.", "Учитель пішов.", SegmentStatus.ACCEPTED),
                decided(
                        3,
                        NarratorPerson.THIRD,
                        "The morning brought no rain and no peace.",
                        "The morning brought no rain and no peace.",
                        SegmentStatus.ACCEPTED),
                decided(4, NarratorPerson.THIRD, "\"Yes,\" he said.", "«Так,\" сказав він.", SegmentStatus.ACCEPTED),
                decided(5, NarratorPerson.THIRD, "The boy waved.", "Хлопчик помахaв.", SegmentStatus.ACCEPTED),
                decided(6, NarratorPerson.THIRD, "The imp waited.", null, SegmentStatus.FLAGGED));
    }

    private static SequenceMetrics measureHandWrittenRun() {
        final LexiconEntry master = LexiconEntry.of("p", "master")
                .seen("господар")
                .seen("учитель")
                .withLearned(new LexiconEntry.Learned("господар", 3, 4));
        final List<ModelCallFinished> calls = List.of(
                new ModelCallFinished(
                        null, CallKind.DRAFT, Duration.ofSeconds(2), null, 0, false, List.of("a", "b", "c"), 1, null),
                new ModelCallFinished("d", CallKind.DRAFT, Duration.ofSeconds(1), null, 0, false),
                new ModelCallFinished("e", CallKind.REVIEW, Duration.ofSeconds(1), null, 0, false));
        final SequenceRun run = new SequenceRun(
                new JobReport(BookFormat.MARKDOWN, JobState.COMPLETED, 7, 6, 1, List.of(), null),
                Duration.ofSeconds(14),
                calls,
                handWrittenSegments(),
                List.of(master),
                Map.of("OK/[ECHO]", 1),
                2,
                3,
                Map.of("UNBALANCED_QUOTES", 2),
                1,
                SequenceNarratorMode.SET);
        return new SequenceMeasure(FIXTURE).measure("hand", "BALANCED", run);
    }

    @Test
    void measure_handWrittenRun_countsEveryTextDefectClass() {
        final SequenceMetrics metrics = measureHandWrittenRun();

        assertThat(metrics.segments()).isEqualTo(7);
        assertThat(metrics.flagged()).isEqualTo(1);
        assertThat(metrics.flaggedWithoutTarget()).isEqualTo(1);
        assertThat(metrics.genderSlips()).isEqualTo(1);
        assertThat(metrics.englishLeftovers()).isEqualTo(1);
        assertThat(metrics.quoteFailures()).isEqualTo(1);
        assertThat(metrics.asciiQuotes()).isEqualTo(1);
        assertThat(metrics.mixedScript()).isEqualTo(1);
        assertThat(term("master", metrics).renderings())
                .containsEntry("господар", 1)
                .containsEntry("учитель", 1);
    }

    @Test
    void measure_handWrittenRun_countsCallsBatchesAndTheLexicon() {
        final SequenceMetrics metrics = measureHandWrittenRun();

        assertThat(metrics.calls()).isEqualTo(3);
        assertThat(metrics.callsPerSegment()).isCloseTo(3.0 / 7, within(0.0001));
        assertThat(metrics.secondsPerSegment()).isCloseTo(2.0, within(0.0001));
        assertThat(metrics.batchedItems()).isEqualTo(3);
        assertThat(metrics.batchFallbackRate()).isCloseTo(1.0 / 3, within(0.0001));
        assertThat(metrics.editsRefused()).isEqualTo(2);
        assertThat(metrics.lexiconDistinct()).isEqualTo(2.0);
        assertThat(metrics.learned()).isEqualTo(1);
        assertThat(metrics.learnedCoverage()).isEqualTo(0.75);
        assertThat(metrics.callsByKind()).containsEntry("DRAFT", 2).containsEntry("REVIEW", 1);
    }

    @Test
    void measure_handWrittenRun_carriesTheRunsLogAndNarratorFacts() {
        final SequenceMetrics metrics = measureHandWrittenRun();

        assertThat(metrics.narrator()).isEqualTo("set");
        assertThat(metrics.hardGateFailuresRound0()).isEqualTo(3);
        assertThat(metrics.hardGateKinds()).containsEntry("UNBALANCED_QUOTES", 2);
        assertThat(metrics.reviewerTruncated()).isEqualTo(1);
        assertThat(term("master", metrics).dominantShare()).isEqualTo(0.5);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "Він сказав \"terms\": []",
                "Він сказав «terms»: []",
                "```json Він сказав",
                "{\"id\": \"1\"} Він сказав"
            })
    void measure_targetHoldingBatchProtocol_countsOneLeak(final String target) {
        final SequenceMetrics metrics = measureOf(
                List.of(decided(0, NarratorPerson.THIRD, "He said.", target, SegmentStatus.ACCEPTED)), List.of());

        assertThat(metrics.leakedProtocol()).isEqualTo(1);
    }

    @Test
    void measure_cleanTarget_countsNoLeak() {
        final SequenceMetrics metrics = measureOf(
                List.of(decided(0, NarratorPerson.THIRD, "He said.", "Він сказав.", SegmentStatus.ACCEPTED)),
                List.of());

        assertThat(metrics.leakedProtocol()).isZero();
    }

    @Test
    void measure_mrAndMrsBothLearnedPani_bothAreClaimedWrongAndMsIsNot() {
        final List<LexiconEntry> lexicon = List.of(
                LexiconEntry.of("p", "mr").withLearned(new LexiconEntry.Learned("пані", 2, 3)),
                LexiconEntry.of("p", "mrs").withLearned(new LexiconEntry.Learned("Пані", 3, 3)),
                LexiconEntry.of("p", "ms").withLearned(new LexiconEntry.Learned("міс", 3, 3)));

        final SequenceMetrics metrics = measureOf(List.of(), lexicon);

        assertThat(metrics.termClaimedWrong()).isEqualTo(2);
        assertThat(metrics.claimedRenderings()).containsExactly("пані <- mr, mrs");
    }

    private static SequenceMetrics measureOf(final List<Decided> segments, final List<LexiconEntry> lexicon) {
        final SequenceRun run = new SequenceRun(
                new JobReport(
                        BookFormat.MARKDOWN, JobState.COMPLETED, segments.size(), segments.size(), 0, List.of(), null),
                Duration.ofSeconds(1),
                List.of(),
                segments,
                lexicon,
                Map.of(),
                0,
                0,
                Map.of(),
                0,
                SequenceNarratorMode.UNSET);
        return new SequenceMeasure(FIXTURE).measure("hand", "BALANCED", run);
    }

    private static TermStat term(final String name, final SequenceMetrics metrics) {
        return metrics.terms().stream()
                .filter(t -> t.term().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static Decided decided(
            final int n,
            final NarratorPerson narrator,
            final String source,
            final String target,
            final SegmentStatus status) {
        return new Decided("s" + n, 0, narrator, source, target, status, SegmentPath.DRAFT, 0);
    }

    @Test
    void report_scriptedMetrics_tableNamesEveryMetricAndJsonCarriesTheSuite() {
        final SequenceReport report = new SequenceReport(scripted);

        assertThat(report.table())
                .contains(
                        "renderings/term",
                        "nameVariants",
                        "genderSlips",
                        "english",
                        "batchFallbacks",
                        "learnedCoverage",
                        "narrator=unset",
                        "hardGateRound0",
                        "leakedProtocol",
                        "reviewerTruncated",
                        "termClaimedWrong",
                        "dominantShare");
        assertThat(report.json())
                .contains("\"suite\" : \"sequence\"", "\"narrator\" : \"unset\"", "\"genderSlips\" : 1");
    }
}

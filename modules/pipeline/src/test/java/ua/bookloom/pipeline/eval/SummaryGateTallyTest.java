package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.pipeline.SequenceJobs;
import ua.bookloom.pipeline.SequenceJobs.Prepared;
import ua.bookloom.pipeline.WireMockProvider;

/**
 * Arm 6's gate figure read from a real run's log: the mini book's chapter 1 is summarised by a canned three-sentence
 * reply whose middle sentence names a glossary entry chapter 1 never names, so the gate keeps two and drops one.
 */
@Tag("slow")
class SummaryGateTallyTest {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    @TempDir
    Path directory;

    @Test
    void summaryGate_sentenceNamingAbsentEntry_oneOfThreeDropped() {
        final SummaryGateTally gate = gateOfRun(null);

        assertThat(gate.chaptersKept()).isEqualTo(1);
        assertThat(gate.sentences()).isEqualTo(3);
        assertThat(gate.kept()).isEqualTo(2);
        assertThat(gate.decisions()).isEqualTo(Map.of("kept", 2, "dropped-entry", 1));
        assertThat(gate.reasked()).isZero();
        assertThat(gate.notKept()).isEmpty();
        assertThat(gate.dropShare()).isCloseTo(0.3333, within(0.0001));
    }

    @Test
    void summaryGate_switchOff_readsNothing() {
        final SummaryGateTally gate = gateOfRun(false);

        assertThat(gate.chaptersKept()).isZero();
        assertThat(gate.sentences()).isZero();
        assertThat(gate.dropShare()).isEqualTo(-1.0);
    }

    private SummaryGateTally gateOfRun(@Nullable final Boolean chapterSummaries) {
        final MiniBook book = MiniBook.load(MiniBook.MINI_EN);
        final MiniRunner runner = new MiniRunner(book, QualityDial.BALANCED, NamePolicy.TRANSLITERATE, null);
        try (WireMockProvider provider = new WireMockProvider(ProviderKind.OLLAMA);
                SequenceLogCapture capture = new SequenceLogCapture()) {
            provider.stubResponder(ChapterSummaryAcceptanceTest::replyTo);
            final Prepared prepared = SequenceJobs.importBook(
                    book.writeEpub(directory), runner.brief().withChapterSummaries(chapterSummaries));
            ChapterSummaryAcceptanceTest.seed(prepared, ChapterSummaryAcceptanceTest.ABSENT_NAME);
            SequenceJobs.run(
                    prepared, provider.model(REQUEST_TIMEOUT, ignored -> {}), ReviewMode.UNATTENDED, null, e -> {});
            return capture.summaryGate();
        }
    }
}

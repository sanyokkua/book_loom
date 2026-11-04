package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;
import static ua.bookloom.pipeline.WholeBookRun.dataOf;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.pipeline.qa.CheckLevel;
import ua.bookloom.pipeline.qa.CheckLevels;

/**
 * Fast spends no fixing call (design D13): a repair-level finding becomes a proofread mark, and only formatting marks
 * the code restore cannot put back earn one plain re-draft, after which the segment is flagged
 * ({@code specs/quality-gates/spec.md} "Make no fixing call at Fast and mark the segment instead", "Flag a segment
 * whose formatting marks stay lost after the one re-draft").
 */
@Tag("slow")
class SegmentTranslatorFastTest {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private static final String SEGMENT = "Book.md:0";

    @TempDir
    Path directory;

    @ParameterizedTest
    @EnumSource(ProviderKind.class)
    void decide_fastRepairFinding_noCallAndProofreadMark(final ProviderKind kind) {
        try (WireMockProvider provider = new WireMockProvider(kind)) {
            provider.stubSequence(List.of(provider.target("Де ключ.", Duration.ZERO)));

            final SegmentView decided = run(provider, "Where is the key?");

            assertThat(provider.chatRequests()).isEqualTo(1);
            assertThat(decided.status()).isEqualTo(SegmentStatus.ACCEPTED);
            assertThat(decided.maskedMachineTarget()).isEqualTo("Де ключ.");
            assertThat(decided.findings())
                    .filteredOn(finding -> finding.raisedBy().equals("terminal-mark"))
                    .singleElement()
                    .satisfies(finding -> assertThat(CheckLevels.of(finding)).isEqualTo(CheckLevel.REPAIR));
        }
    }

    @ParameterizedTest
    @EnumSource(ProviderKind.class)
    void decide_fastMarksStillLost_redraftsOnceThenFlags(final ProviderKind kind) {
        try (WireMockProvider provider = new WireMockProvider(kind)) {
            provider.stubSequence(
                    List.of(provider.target("Відчинив", Duration.ZERO), provider.target("ВІДЧИНИВ", Duration.ZERO)));

            final SegmentView decided = run(provider, "He opened the *old* door.");

            assertThat(provider.chatRequests()).isEqualTo(2);
            assertThat(WholeBookRun.userMessage(provider.chatBodies().getLast()))
                    .doesNotContain("[Defects to fix")
                    .doesNotContain("[Previous attempt");
            assertThat(decided.status()).isEqualTo(SegmentStatus.FLAGGED);
            assertThat(decided.maskedMachineTarget()).isNull();
            assertThat(decided.rejectedTarget()).isEqualTo("ВІДЧИНИВ");
            assertThat(decided.findings())
                    .anyMatch(finding -> finding.raisedBy().equals("placeholder"));
        }
    }

    private SegmentView run(final WireMockProvider provider, final String source) {
        final Path book = TestBooks.markdown(directory.resolve("Book.md"), source);
        final WholeBookRun.Project project = WholeBookRun.project(book, QualityDial.FAST);
        report(project.job(ReviewMode.UNATTENDED, provider.model(REQUEST_TIMEOUT, ignored -> {}))
                .run());
        return dataOf(project.desk().segment(project.id(), SEGMENT));
    }
}

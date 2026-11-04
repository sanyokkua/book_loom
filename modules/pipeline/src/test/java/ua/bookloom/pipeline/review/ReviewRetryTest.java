package ua.bookloom.pipeline.review;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.review.ReviewFixtures.FLAGGED_ID;
import static ua.bookloom.pipeline.review.ReviewFixtures.flag;
import static ua.bookloom.pipeline.review.ReviewFixtures.latestRun;
import static ua.bookloom.pipeline.review.ReviewFixtures.withContext;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.WireMockProvider;
import ua.bookloom.pipeline.review.ReviewFixtures.Desk;

/**
 * The desk's Try again decides the retried draft by the hard gates, the checks and the acceptance rule, at the HTTP
 * seam, and makes no repair call ({@code specs/review-queue/spec.md} "Decide a retry by the checks and the acceptance
 * rule without a repair").
 */
@Tag("slow")
class ReviewRetryTest {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    @TempDir
    private Path tempDir;

    // 14 characters for 31 fails the length band: a finding is left on the retried draft, and only the draft is sent.
    @ParameterizedTest
    @EnumSource(ProviderKind.class)
    void retry_draftWithResidue_isDecidedWithoutARepairCall(final ProviderKind kind) {
        try (WireMockProvider provider = new WireMockProvider(kind)) {
            provider.stubSequence(List.of(provider.singleTarget(RetryDraftTest.TOO_SHORT, Duration.ZERO)));
            final Desk desk = ReviewFixtures.epub(tempDir);
            latestRun(desk, JobState.PAUSED);
            flag(desk, FLAGGED_ID, "Чудовисько зустріло мене.");
            withContext(desk, FLAGGED_ID, RetryDraftTest.SNAPSHOT);

            final Result<SegmentRecord> retried = desk.retryDraft(ReviewMode.ASSISTED)
                    .retry(desk.projectId(), FLAGGED_ID, null, false, provider.model(REQUEST_TIMEOUT, ignored -> {}));

            final SegmentRecord record = Objects.requireNonNull(retried.data(), () -> "retry: " + retried.error());
            assertThat(provider.chatRequests()).isEqualTo(1);
            assertThat(record.status()).isEqualTo(SegmentStatus.FLAGGED);
            assertThat(record.findings()).extracting(QaFinding::raisedBy).contains("length");
        }
    }
}

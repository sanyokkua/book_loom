package ua.bookloom.ui.state;

import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.TokenUsage;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.ChunkPosition;
import ua.bookloom.api.pipeline.ModelCallFinished;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.pipeline.SegmentDecided;
import ua.bookloom.api.pipeline.SegmentDetail;
import ua.bookloom.api.pipeline.SegmentStarted;
import ua.bookloom.api.project.SegmentPath;

/** Fixtures for the tests that drive a {@link RunSession} with events and read what the mirror's live section shows. */
abstract class LiveSessionTestBase extends RunnerTestBase {

    static final ChunkPosition POSITION = new ChunkPosition(7, 11, 1, 3);

    protected final MutableClock clock = new MutableClock();

    /** A session on a Balanced run whose desk reads happen at once, on the calling thread. */
    protected RunSession session() {
        return session(QualityDial.BALANCED);
    }

    protected RunSession session(final QualityDial dial) {
        final RunContext context = new RunContext(PROJECT_ID, FILE_NAME, ReviewMode.UNATTENDED, dial, SELECTION);
        return new RunSession(mirror, clock, context, desk, new DirectExecutor());
    }

    protected void tick(final RunSession session) {
        session.tick();
        WaitForAsyncUtils.waitForFxEvents();
    }

    protected LiveRows rows() {
        return onFx(() -> mirror.live().liveRows().get());
    }

    protected Throughput throughput() {
        return onFx(() -> mirror.live().throughput().get());
    }

    static SegmentStarted started(final String id, final String locator, final String source) {
        return new SegmentStarted(id, locator, source, POSITION);
    }

    static SegmentDecided decidedWith(final String id, final SegmentStatus status, final SegmentDetail detail) {
        return new SegmentDecided(id, status, null, progress(1, 0, 1), detail);
    }

    static SegmentDetail detail(final @Nullable Double score, final SegmentPath path) {
        return new SegmentDetail(score, path, List.of());
    }

    static ModelCallFinished call(final String segmentId, final CallKind kind) {
        return new ModelCallFinished(segmentId, kind, Duration.ofSeconds(1), null, 10, false);
    }

    static ModelCallFinished draftCall(final int completion, final Duration generation, final boolean estimated) {
        return new ModelCallFinished(
                "s-1", CallKind.DRAFT, generation, new TokenUsage(null, completion, generation), 300, estimated);
    }

    /** Decides {@code count} segments, each {@code seconds} after the last, with 100 segments still pending. */
    protected void decideEvery(final RunSession session, final int count, final long seconds) {
        IntStream.rangeClosed(1, count).forEach(n -> {
            clock.advance(Duration.ofSeconds(seconds));
            session.onEvent(new SegmentDecided("s-" + n, SegmentStatus.ACCEPTED, null, progress(n, 0, 100), null));
        });
    }
}

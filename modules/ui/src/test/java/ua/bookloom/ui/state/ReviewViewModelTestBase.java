package ua.bookloom.ui.state;

import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import org.jspecify.annotations.Nullable;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.ReviewCounts;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.Severity;
import ua.bookloom.ui.i18n.Messages;

/** What the review view model tests share: an open book, the recording desk and the segments the desk holds. */
// The view model is built by each test after it has scripted its fakes, which NullAway cannot see.
@SuppressWarnings("NullAway.Init")
abstract class ReviewViewModelTestBase extends TranslatingViewModelTestBase {

    static final String MASKED_SOURCE = "He opened the ⟦g0⟧old⟦g1⟧ door.";
    static final String MASKED_MACHINE = "Він відчинив ⟦g0⟧старі⟦g1⟧ двері.";

    protected ReviewViewModel review;
    protected String projectId;

    protected static SegmentView view(
            final String id,
            final String locator,
            final SegmentStatus status,
            final List<QaFinding> findings,
            final @Nullable Double judgeScore) {
        return new SegmentView(
                id,
                locator,
                SegmentKind.PARAGRAPH,
                status,
                MASKED_SOURCE,
                "He opened the old door.",
                MASKED_MACHINE,
                null,
                null,
                findings,
                judgeScore,
                SegmentPath.DRAFT,
                false,
                null,
                null);
    }

    protected static SegmentView lowScore() {
        return view("ch05.xhtml:11", "ch5 · p12", SegmentStatus.FLAGGED, List.of(), 0.58);
    }

    protected static SegmentView nameIssue() {
        return view(
                "ch07.xhtml:39",
                "ch7 · p40",
                SegmentStatus.FLAGGED,
                List.of(new QaFinding("glossary", Severity.MEDIUM, "Name changed", "glossary")),
                null);
    }

    protected static SegmentView wrongLanguage() {
        return view(
                "ch09.xhtml:2",
                "ch9 · p03",
                SegmentStatus.FLAGGED,
                List.of(new QaFinding("language", Severity.MEDIUM, "Looks Russian", "script")),
                null);
    }

    protected static SegmentView withEditor(
            final SegmentView base, final @Nullable String machine, final @Nullable String user) {
        return new SegmentView(
                base.segmentId(),
                base.locator(),
                base.kind(),
                base.status(),
                base.maskedSource(),
                base.displaySource(),
                machine,
                user,
                user,
                base.findings(),
                base.judgeScore(),
                base.path(),
                base.reviewed(),
                null,
                null);
    }

    /** A flagged segment with no target at all, only the model's refused reply. */
    protected static SegmentView withRejected(final SegmentView base, final String rejected) {
        return new SegmentView(
                base.segmentId(),
                base.locator(),
                base.kind(),
                base.status(),
                base.maskedSource(),
                base.displaySource(),
                null,
                null,
                null,
                base.findings(),
                base.judgeScore(),
                base.path(),
                base.reviewed(),
                null,
                null,
                rejected);
    }

    protected static ReviewCounts flaggedCount(final int flagged) {
        return new ReviewCounts(100, 90, 0, flagged, 0, 0, 0, 0, 0);
    }

    /** Opens the book, scripts three flagged segments and builds the view model; call once. */
    protected void buildReview() {
        buildReview(new DirectExecutor());
    }

    /** As {@link #buildReview()}, with desk calls going to {@code deskExecutor}. */
    protected void buildReview(final ExecutorService deskExecutor) {
        openBook();
        projectId = onFx(() -> current.book().get().projectId());
        desk.willAnswerQueue(List.of(lowScore(), nameIssue(), wrongLanguage()));
        desk.willAnswerSegment(lowScore());
        desk.willAnswerSegment(nameIssue());
        desk.willAnswerSegment(wrongLanguage());
        desk.willAnswerCounts(flaggedCount(3));
        review = onFx(() -> newReviewViewModel(deskExecutor));
        WaitForAsyncUtils.waitForFxEvents();
    }

    protected ReviewViewModel newReviewViewModel(final ExecutorService deskExecutor) {
        final ReviewRetry retry = new ReviewRetry(desk, models, mirror, settings, new Messages(() -> Locale.ENGLISH));
        return new ReviewViewModel(desk, mirror, current, reviewMode, toasts, errors, retry, deskExecutor);
    }

    protected void open() {
        press(review::open);
    }

    protected void select(final String segmentId) {
        press(() -> review.select(segmentId));
    }

    protected void setRunState(final RunState state) {
        onFx(() -> {
            mirror.publishRunState(state);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    protected List<String> locators() {
        return onFx(() -> review.rows().stream().map(ReviewRow::locator).toList());
    }
}

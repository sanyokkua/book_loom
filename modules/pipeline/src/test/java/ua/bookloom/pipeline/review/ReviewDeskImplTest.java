package ua.bookloom.pipeline.review;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.review.ReviewFixtures.FLAGGED_ID;
import static ua.bookloom.pipeline.review.ReviewFixtures.MONSTER_TARGET;
import static ua.bookloom.pipeline.review.ReviewFixtures.NAMES_ID;
import static ua.bookloom.pipeline.review.ReviewFixtures.accept;
import static ua.bookloom.pipeline.review.ReviewFixtures.flag;
import static ua.bookloom.pipeline.review.ReviewFixtures.latestRun;
import static ua.bookloom.pipeline.review.ReviewFixtures.stored;
import static ua.bookloom.pipeline.review.ReviewFixtures.withContext;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.ReviewCounts;
import ua.bookloom.api.pipeline.ReviewFilter;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.DeferralReason;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.review.ReviewFixtures.Desk;

/** The port delegates every action and read to its part and answers the part's result, never an exception. */
class ReviewDeskImplTest {

    private static final String EDIT = "Чудовисько стріло мене опівночі.";
    private static final String REVISED_ID = "ch03.xhtml:1";
    private static final String PROPOSAL_ID = "ch02.xhtml:6";
    private static final String UNKNOWN_ID = "ch99.xhtml:0";

    @TempDir
    private Path tempDir;

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Logger bookloom;
    private @Nullable Level previousLevel;

    /** One action of the port, applied to a segment id. */
    enum Action {
        ACCEPT(FLAGGED_ID) {
            @Override
            Result<SegmentRecord> apply(final Desk desk, final ReviewDeskImpl port, final String id) {
                return port.accept(desk.projectId(), id);
            }
        },
        SAVE_EDIT(FLAGGED_ID) {
            @Override
            Result<SegmentRecord> apply(final Desk desk, final ReviewDeskImpl port, final String id) {
                return port.saveEdit(desk.projectId(), id, EDIT);
            }
        },
        REVERT(REVISED_ID) {
            @Override
            Result<SegmentRecord> apply(final Desk desk, final ReviewDeskImpl port, final String id) {
                return port.revert(desk.projectId(), id);
            }
        },
        RETRY(FLAGGED_ID) {
            @Override
            Result<SegmentRecord> apply(final Desk desk, final ReviewDeskImpl port, final String id) {
                return port.retry(desk.projectId(), id, null, false, RetryDraftTest.passing());
            }
        },
        SKIP(FLAGGED_ID) {
            @Override
            Result<SegmentRecord> apply(final Desk desk, final ReviewDeskImpl port, final String id) {
                return port.skip(desk.projectId(), id);
            }
        },
        APPLY_PROPOSAL(PROPOSAL_ID) {
            @Override
            Result<SegmentRecord> apply(final Desk desk, final ReviewDeskImpl port, final String id) {
                return port.acceptProposal(desk.projectId(), id);
            }
        };

        private final String segmentId;

        Action(final String segmentId) {
            this.segmentId = segmentId;
        }

        abstract Result<SegmentRecord> apply(Desk desk, ReviewDeskImpl port, String id);
    }

    @BeforeEach
    void attachAppender() {
        bookloom = (Logger) LoggerFactory.getLogger("ua.bookloom");
        previousLevel = bookloom.getLevel();
        appender.start();
        bookloom.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        bookloom.detachAppender(appender);
        bookloom.setLevel(previousLevel);
        appender.stop();
    }

    @ParameterizedTest
    @EnumSource(Action.class)
    void action_decidedSegment_neverLeavesItPending(final Action action) {
        // no action returns a segment to PENDING
        final Desk desk = prepared();

        final Result<SegmentRecord> result = action.apply(desk, desk.reviewDesk(ReviewMode.ASSISTED), action.segmentId);

        assertThat(result.isOk()).isTrue();
        assertThat(stored(desk, action.segmentId).status()).isNotEqualTo(SegmentStatus.PENDING);
    }

    @ParameterizedTest
    @EnumSource(Action.class)
    void action_unknownSegment_isValidationWithoutThrowing(final Action action) {
        // an id the project does not hold is answered, not thrown
        final Desk desk = prepared();

        final Result<SegmentRecord> result = action.apply(desk, desk.reviewDesk(ReviewMode.ASSISTED), UNKNOWN_ID);

        assertThat(error(result).code()).isEqualTo(ErrorCode.validation);
    }

    @Test
    void accept_flaggedSegment_answersTheAcceptedRecord() {
        // the desk answers what SegmentActions.accept stored
        final Desk desk = prepared();

        final SegmentRecord record = ok(desk.reviewDesk(ReviewMode.ASSISTED).accept(desk.projectId(), FLAGGED_ID));

        assertThat(record.status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(record).isEqualTo(stored(desk, FLAGGED_ID));
    }

    @Test
    void saveEdit_flaggedSegment_answersTheRevisedRecord() {
        // the desk answers what SegmentActions.saveEdit stored
        final Desk desk = prepared();

        final SegmentRecord record =
                ok(desk.reviewDesk(ReviewMode.ASSISTED).saveEdit(desk.projectId(), FLAGGED_ID, EDIT));

        assertThat(record.status()).isEqualTo(SegmentStatus.REVISED);
        assertThat(record.userTarget()).isEqualTo(EDIT);
    }

    @Test
    void revert_revisedSegment_answersTheAcceptedRecord() {
        // the desk answers what SegmentActions.revert stored
        final Desk desk = prepared();

        final SegmentRecord record = ok(desk.reviewDesk(ReviewMode.ASSISTED).revert(desk.projectId(), REVISED_ID));

        assertThat(record.status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(record.userTarget()).isNull();
    }

    @Test
    void skip_flaggedSegment_answersTheNextFlaggedRecord() {
        // Skip changes nothing and answers the record the queue moves on to
        final Desk desk = prepared();
        flag(desk, NAMES_ID, "Гейл пішов.");
        final SegmentRecord before = stored(desk, FLAGGED_ID);

        final SegmentRecord next = ok(desk.reviewDesk(ReviewMode.ASSISTED).skip(desk.projectId(), FLAGGED_ID));

        assertThat(next).isEqualTo(stored(desk, NAMES_ID));
        assertThat(stored(desk, FLAGGED_ID)).isEqualTo(before);
    }

    @Test
    void acceptProposal_storedProposal_answersTheRevisedRecordWithIt() {
        // the desk answers what SegmentActions.acceptProposal stored
        final Desk desk = prepared();

        final SegmentRecord record =
                ok(desk.reviewDesk(ReviewMode.ASSISTED).acceptProposal(desk.projectId(), PROPOSAL_ID));

        assertThat(record.status()).isEqualTo(SegmentStatus.REVISED);
        assertThat(record.userTarget()).isEqualTo("Він пішов.");
    }

    @Test
    void retry_runningRun_answersBusy() {
        // the desk answers the retry's own refusal
        final Desk desk = prepared();
        latestRun(desk, JobState.RUNNING);
        final ScriptedChatModel model = RetryDraftTest.passing();

        final Result<SegmentRecord> result =
                desk.reviewDesk(ReviewMode.ASSISTED).retry(desk.projectId(), FLAGGED_ID, null, false, model);

        assertThat(error(result).code()).isEqualTo(ErrorCode.busy);
        assertThat(model.requests()).isEmpty();
    }

    @Test
    void queue_allFlagged_answersWhatTheQueriesAnswer() {
        // the queue is ReviewQueries' own
        final Desk desk = prepared();
        flag(desk, NAMES_ID, "Гейл пішов.");

        final Result<?> queue = desk.reviewDesk(ReviewMode.ASSISTED).queue(desk.projectId(), ReviewFilter.ALL_FLAGGED);

        assertThat(queue.data())
                .isNotNull()
                .isEqualTo(desk.queries()
                        .queue(desk.projectId(), ReviewFilter.ALL_FLAGGED)
                        .data());
    }

    @Test
    void segment_flaggedSegment_answersWhatTheQueriesAnswer() {
        // one segment's view is ReviewQueries' own
        final Desk desk = prepared();

        final Result<?> view = desk.reviewDesk(ReviewMode.ASSISTED).segment(desk.projectId(), FLAGGED_ID);

        assertThat(view.data())
                .isNotNull()
                .isEqualTo(desk.queries().segment(desk.projectId(), FLAGGED_ID).data());
    }

    @Test
    void counts_threeAcceptedOneFlagged_countsTheProjectsRecords() {
        // the counts come from the one counts rule over the project's records
        final Desk desk = ReviewFixtures.markdown(tempDir, "One.\n\nTwo.\n\nThree.\n\nFour.\n");
        accept(desk, "Book.md:0", "Один.");
        accept(desk, "Book.md:1", "Два.");
        accept(desk, "Book.md:2", "Три.");
        flag(desk, "Book.md:3", "Чотири.");

        final ReviewCounts counts = Objects.requireNonNull(
                desk.reviewDesk(ReviewMode.ASSISTED).counts(desk.projectId()).data(), "counts");

        assertThat(counts).isEqualTo(new ReviewCounts(4, 3, 0, 1, 0, 0, 0, 0));
    }

    @Test
    void counts_repositoryThrows_isInternalWithTheCauseAndOneErrorLine() {
        // an unexpected failure is caught at the boundary, kept as the cause and logged once
        final Desk desk = prepared();
        final IllegalStateException failure = new IllegalStateException("store failed");
        final ReviewDeskImpl port = new ReviewDeskImpl(
                desk.actions(),
                desk.queries(),
                desk.retryDraft(ReviewMode.ASSISTED),
                desk.projects(),
                throwing(failure));

        final Result<ReviewCounts> result = port.counts(desk.projectId());

        assertThat(error(result).code()).isEqualTo(ErrorCode.internal);
        assertThat(error(result).cause()).isSameAs(failure);
        assertThat(appender.list)
                .filteredOn(event -> event.getLevel() == Level.ERROR)
                .hasSize(1);
    }

    @Test
    void accept_partsRepositoryThrows_isInternalWithTheCause() {
        // a part's unexpected failure never crosses the port as an exception
        final Desk desk = prepared();
        final IllegalStateException failure = new IllegalStateException("store failed");
        final SegmentActions actions = new SegmentActions(
                desk.documents(), desk.openProjects(), desk.projects(), throwing(failure), desk.deferrals());
        final ReviewDeskImpl port = new ReviewDeskImpl(
                actions, desk.queries(), desk.retryDraft(ReviewMode.ASSISTED), desk.projects(), desk.segments());

        final Result<SegmentRecord> result = port.accept(desk.projectId(), FLAGGED_ID);

        assertThat(error(result).code()).isEqualTo(ErrorCode.internal);
        assertThat(error(result).cause()).isSameAs(failure);
    }

    /** FLAGGED monster with its snapshot, a REVISED segment, and an edited segment with a proposal waiting. */
    private Desk prepared() {
        final Desk desk = ReviewFixtures.epub(tempDir);
        latestRun(desk, JobState.PAUSED);
        flag(desk, FLAGGED_ID, MONSTER_TARGET);
        withContext(desk, FLAGGED_ID, RetryDraftTest.SNAPSHOT);
        accept(desk, REVISED_ID, "Розділ 3, рядок 1.");
        ok(desk.actions().saveEdit(desk.projectId(), REVISED_ID, "Розділ третій, рядок перший."));
        accept(desk, PROPOSAL_ID, "Вона пішла.");
        ok(desk.actions().saveEdit(desk.projectId(), PROPOSAL_ID, "Вона пішла."));
        Objects.requireNonNull(desk.deferrals()
                .add(new Deferral(
                        "d1",
                        desk.projectId(),
                        PROPOSAL_ID,
                        DeferralReason.GENDER_UNKNOWN,
                        "Hale",
                        null,
                        "Він пішов.",
                        "Він пішов."))
                .data());
        return desk;
    }

    private static SegmentRepository throwing(final RuntimeException failure) {
        return (SegmentRepository) Proxy.newProxyInstance(
                SegmentRepository.class.getClassLoader(),
                new Class<?>[] {SegmentRepository.class},
                (proxy, method, arguments) -> {
                    throw failure;
                });
    }

    private static SegmentRecord ok(final Result<SegmentRecord> result) {
        return Objects.requireNonNull(result.data(), () -> "expected success but got " + result.error());
    }

    private static AppError error(final Result<?> result) {
        return Objects.requireNonNull(result.error(), () -> "expected an error but got " + result.data());
    }
}

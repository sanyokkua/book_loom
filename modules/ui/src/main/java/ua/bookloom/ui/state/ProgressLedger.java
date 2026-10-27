package ua.bookloom.ui.state;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.StepKind;
import ua.bookloom.api.pipeline.StepResult;
import ua.bookloom.ui.state.ProgressFigures.Figures;
import ua.bookloom.ui.state.ProgressFigures.KindFigures;

/**
 * Counts every step an operation announces, of every kind, into one share that never falls and a time left taken from
 * the measured time per kind of step.
 *
 * <p>The share is steps done over steps planned. A translation announces its steps per chunk, so the ledger is told
 * the book's segment count ({@link #seed}) and plans the drafts of the whole book from it, and forecasts the checks of
 * chunks not entered yet from the checks per draft announced so far; otherwise the bar would be full at the end of
 * every chunk. The share is a high-water mark of the shares read, so work added on the way holds the bar until the
 * work catches up; read it only once the book's size is known, or a chunk's own total would set the mark.
 *
 * <p>The time left is, over every kind, the steps left times that kind's measured seconds per step, shown once the
 * operation has finished 5 steps or 3 % of them. A step's seconds are the running time since the previous step
 * ended, so the time between steps lands on a step and the sum equals the real time; a step that needed no call
 * is done but takes no time of its own. A kind not run yet borrows the draft time. Not thread-safe: {@link RunSession}
 * calls it under its publish lock, and a progress dialog reads it on the FX thread only.
 */
@Slf4j
final class ProgressLedger {

    /** Steps the operation must finish before a time left is shown. */
    static final int MIN_STEPS = 5;

    /** Share of the steps the operation must finish before a time left is shown, when it comes before the steps. */
    static final double MIN_SHARE = 0.03;

    /** What a check costs against a draft, until checks have been timed. */
    private static final double CHECK_WEIGHT = 0.6;

    private static final double MILLIS_PER_SECOND = 1000.0;

    /**
     * One chunk of a stored project, for rebuilding the ledger of a reopened one.
     *
     * @param segments the segments the chunk holds
     * @param decided how many of them are decided
     */
    record ChunkState(int segments, int decided) {}

    private static final class Tally {
        private int basePlanned;
        private int baseDone;
        private int planned;
        private int done;
        private int timed;
        private double seconds;

        @Nullable
        Double secondsPerStep() {
            return timed == 0 ? null : seconds / timed;
        }
    }

    private final RunClock clock;
    private final Map<StepKind, Tally> tallies = new EnumMap<>(StepKind.class);
    private final TimeLeftShaper shaper = new TimeLeftShaper();
    private int bookSegments;
    private int decided;
    private int baselineDecided;
    private boolean seeded;
    private boolean rebuilt;
    private int finished;
    private double reached;
    private Duration markedAt = Duration.ZERO;

    ProgressLedger(final RunClock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
        // Both exist from the start, so reading the ratio between them never changes the map while it is walked.
        tally(StepKind.DRAFT);
        tally(StepKind.CHECK);
    }

    /**
     * Makes the ledger of a reopened project from what is stored: every decided segment is a finished draft, and
     * every chunk whose segments are all decided has its checks finished.
     *
     * @param clock the clock the new run is measured by
     * @param chunks the project's chunks in order
     * @param checksPerChunk the checks one chunk gets at the project's quality
     * @return a ledger that starts where the work stands and shows no time left until it has timed its own steps
     */
    static ProgressLedger rebuilt(final RunClock clock, final List<ChunkState> chunks, final int checksPerChunk) {
        Objects.requireNonNull(chunks, "chunks");
        final ProgressLedger ledger = new ProgressLedger(clock);
        final Tally drafts = ledger.tally(StepKind.DRAFT);
        final Tally checks = ledger.tally(StepKind.CHECK);
        for (final ChunkState chunk : chunks) {
            drafts.basePlanned += chunk.segments();
            drafts.baseDone += chunk.decided();
            checks.basePlanned += checksPerChunk;
            checks.baseDone += chunk.decided() >= chunk.segments() ? checksPerChunk : 0;
        }
        ledger.bookSegments = drafts.basePlanned;
        ledger.decided = drafts.baseDone;
        ledger.baselineDecided = drafts.baseDone;
        ledger.seeded = true;
        ledger.rebuilt = true;
        log.debug(
                "progress ledger rebuilt from {} chunks: {} of {} drafts, {} of {} checks",
                chunks.size(),
                drafts.baseDone,
                drafts.basePlanned,
                checks.baseDone,
                checks.basePlanned);
        return ledger;
    }

    /**
     * Tells the ledger how large the book is and how much of it is decided, which the steps announced chunk by chunk
     * cannot say.
     *
     * @param bookSegments the segments of the whole book
     * @param decided the segments decided so far, those of an earlier run included
     */
    void seed(final int bookSegments, final int decided) {
        log.trace("progress ledger seeded: {} of {} segments decided", decided, bookSegments);
        if (!seeded) {
            baselineDecided = decided;
            seeded = true;
        }
        this.bookSegments = bookSegments;
        this.decided = decided;
    }

    /**
     * Adds planned steps to the total; the bar holds where it was.
     *
     * @param kind what the steps are
     * @param added how many join the total
     */
    void planned(final StepKind kind, final int added) {
        Objects.requireNonNull(kind, "kind");
        log.debug("progress ledger: {} {} steps planned", added, kind);
        tally(kind).planned += added;
    }

    /**
     * Records that one step ended.
     *
     * @param kind what the step was
     * @param result how it ended
     * @param now the moment it ended
     */
    void ended(final StepKind kind, final StepResult result, final Instant now) {
        ended(kind, result, 1, now);
    }

    /**
     * Records that steps ended together.
     *
     * @param kind what the steps were
     * @param result how they ended
     * @param count how many ended; their time is shared among them
     * @param now the moment they ended
     */
    void ended(final StepKind kind, final StepResult result, final int count, final Instant now) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(result, "result");
        final Tally tally = tally(kind);
        tally.done += count;
        finished += count;
        if (result != StepResult.NO_CALL) {
            final Duration active = clock.active(now);
            if (result == StepResult.DONE) {
                tally.timed += count;
                tally.seconds += active.minus(markedAt).toMillis() / MILLIS_PER_SECOND;
            }
            markedAt = active;
        }
        log.debug("progress ledger: {} {} step(s) ended as {}, {} finished", count, kind, result, finished);
    }

    /**
     * The measured seconds of one drafted segment in this run, which is what the next estimate of the model needs.
     *
     * @return the seconds per timed draft, or {@code null} when no draft was timed
     */
    @Nullable
    Double draftSecondsPerSegment() {
        return tally(StepKind.DRAFT).secondsPerStep();
    }

    /**
     * Reads the figures at a moment.
     *
     * @param now the moment; the time left is shaped against the run's clock at it
     * @return the figures; the percent and the bar never fall between two reads
     */
    Figures read(final Instant now) {
        Objects.requireNonNull(now, "now");
        final List<KindFigures> kinds = kinds();
        raise(kinds);
        final int planned = kinds.stream().mapToInt(KindFigures::planned).sum();
        final int done = kinds.stream().mapToInt(KindFigures::done).sum();
        final Duration left = timeLeft(kinds, planned, now);
        log.trace("progress ledger read: {} of {} steps, bar {}, left {}", done, planned, reached, left);
        return new Figures(
                Figures.percentOf(reached, planned > 0 && done >= planned), done, planned, left, reached, kinds);
    }

    private Tally tally(final StepKind kind) {
        return tallies.computeIfAbsent(kind, key -> new Tally());
    }

    private void raise(final List<KindFigures> kinds) {
        final int planned = kinds.stream().mapToInt(KindFigures::planned).sum();
        final int done = kinds.stream().mapToInt(KindFigures::done).sum();
        if (planned > 0) {
            reached = Math.max(reached, (double) done / planned);
        }
    }

    private List<KindFigures> kinds() {
        final List<KindFigures> kinds = new ArrayList<>();
        tallies.forEach((kind, tally) -> {
            final int done = doneOf(kind, tally);
            final int planned = plannedOf(kind, tally, done);
            if (planned > 0) {
                kinds.add(new KindFigures(kind, planned, done, tally.secondsPerStep()));
            }
        });
        return kinds;
    }

    private int plannedOf(final StepKind kind, final Tally tally, final int done) {
        final int announced = tally.basePlanned + tally.planned;
        final int wanted =
                switch (kind) {
                    case DRAFT -> Math.max(announced, bookSegments);
                    case CHECK -> Math.max(announced, forecastChecks());
                    default -> announced;
                };
        return Math.max(wanted, done);
    }

    private int doneOf(final StepKind kind, final Tally tally) {
        final int done = tally.baseDone + tally.done;
        return switch (kind) {
            case DRAFT -> Math.max(done, decided);
            case CHECK -> done + assumedChecks();
            default -> done;
        };
    }

    private double checksPerDraft() {
        final Tally drafts = tally(StepKind.DRAFT);
        final Tally checks = tally(StepKind.CHECK);
        final int announced = drafts.basePlanned + drafts.planned;
        return announced == 0 ? 0 : (checks.basePlanned + checks.planned) / (double) announced;
    }

    private int forecastChecks() {
        final Tally drafts = tally(StepKind.DRAFT);
        final int book = Math.max(bookSegments, drafts.basePlanned + drafts.planned);
        return (int) Math.ceil(checksPerDraft() * book);
    }

    // A continued run announces only the chunks it enters, so the checks of the segments decided before it started are
    // taken as done at the rate the run announces them; a rebuilt ledger knows them exactly.
    private int assumedChecks() {
        return rebuilt ? 0 : (int) Math.floor(checksPerDraft() * baselineDecided);
    }

    private @Nullable Duration timeLeft(final List<KindFigures> kinds, final int total, final Instant now) {
        if (finished == 0 || (finished < MIN_STEPS && finished < MIN_SHARE * total)) {
            return null;
        }
        final Double draftSeconds = tally(StepKind.DRAFT).secondsPerStep();
        double seconds = 0;
        for (final KindFigures kind : kinds) {
            final int left = kind.planned() - kind.done();
            if (left <= 0) {
                continue;
            }
            final Double each = secondsPerStep(kind, draftSeconds);
            if (each == null) {
                return null;
            }
            seconds += left * each;
        }
        return seconds <= 0
                ? null
                : shaper.shape(Duration.ofMillis(Math.round(seconds * MILLIS_PER_SECOND)), clock.active(now));
    }

    // A stage is a group row of the names pass, whose stages differ too much for a mean to say anything.
    private static @Nullable Double secondsPerStep(final KindFigures kind, final @Nullable Double draftSeconds) {
        if (kind.kind() == StepKind.STAGE) {
            return null;
        }
        if (kind.secondsPerStep() != null) {
            return kind.secondsPerStep();
        }
        if (draftSeconds == null) {
            return null;
        }
        return kind.kind() == StepKind.CHECK ? draftSeconds * CHECK_WEIGHT : draftSeconds;
    }
}

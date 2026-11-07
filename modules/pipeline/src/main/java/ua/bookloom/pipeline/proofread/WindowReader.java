package ua.bookloom.pipeline.proofread;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.JobProgress;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.StepKind;
import ua.bookloom.api.pipeline.StepResult;
import ua.bookloom.api.project.ChunkCommit;
import ua.bookloom.api.project.ProofreadVerdict;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.prompt.StepAnnouncer;
import ua.bookloom.pipeline.run.RoutedCalls;
import ua.bookloom.pipeline.run.RunEnd;
import ua.bookloom.pipeline.run.RunSettings;
import ua.bookloom.pipeline.run.RunStores;
import ua.bookloom.pipeline.run.Step;

/**
 * Reads one proofread window (design D15 "Windows", "Resume"): plans its call with no model call so its fingerprint
 * can be compared with the verdicts its focus holds, sends it as one PROOFREAD step routed like a draft — a pause makes
 * the call again on resume, a stop leaves the window undecided, a window past its budget of pauses is left unread — and
 * commits its texts, verdicts and settled deferrals in one transaction under the fingerprint of the window as it now
 * stands, so a Continue finds it read.
 *
 * <p>Used from the job thread only.
 */
@Slf4j
final class WindowReader {

    private static final String STEP_LABEL = "proofread";

    private final ProofreadStage.Windows tools;
    private final RunSettings settings;
    private final RunStores stores;
    private final StepAnnouncer steps;
    private final Consumer<JobEvent> emit;

    /**
     * One window's question: its texts, its planned call and the fingerprint they make.
     *
     * @param round the round the window is read in
     * @param window the window
     * @param texts the window's texts as committed now
     * @param planned the planned call, or null when it could not be planned (the call then routes the plan's error)
     * @param fingerprint the window's fingerprint; empty when it could not be planned
     */
    record Question(
            int round,
            ProofreadWindow window,
            ProofreadCall.Texts texts,
            ProofreadCall.@Nullable Planned planned,
            String fingerprint) {

        /** {@return the step's key, unique within the run} */
        String key() {
            return "r" + round + ":" + window.label();
        }
    }

    WindowReader(
            final ProofreadStage.Windows tools,
            final RunSettings settings,
            final RunStores stores,
            final Consumer<JobEvent> emit) {
        this.tools = Objects.requireNonNull(tools, "tools");
        this.emit = Objects.requireNonNull(emit, "emit");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.stores = Objects.requireNonNull(stores, "stores");
        this.steps = tools.call().steps();
    }

    /** {@return the window's question as the reading stands now} */
    Question question(final int round, final ProofreadWindow window, final WindowReading reading) {
        final ProofreadCall.Texts texts = reading.texts(window);
        final Result<ProofreadCall.Planned> planned = tools.call().plan(window, texts, reading.map());
        final ProofreadCall.@Nullable Planned held = planned.data();
        if (held == null) {
            log.debug(
                    "Proofread window {} could not be planned code={}; it is read and its error routed",
                    window.label(),
                    Objects.requireNonNull(planned.error(), "error").code());
            return new Question(round, window, texts, null, "");
        }
        return new Question(round, window, texts, held, WindowFingerprint.of(round, window, texts, held.mapLines()));
    }

    /**
     * Whether a window was already read with the facts it has now: every focus segment holds a verdict with its
     * fingerprint, or — for a first-round window — a verdict of Max's whole-book round, which read it after the first
     * round and is checked by its own fingerprint there.
     *
     * @param question the non-null question
     * @param reading the non-null reading
     * @return {@code true} if the window needs no call, {@code false} otherwise
     */
    boolean isRead(final Question question, final WindowReading reading) {
        if (question.planned() == null) {
            return false;
        }
        final boolean read = question.window().focusIds().stream()
                .allMatch(id -> holds(reading.record(id).proofread(), question));
        if (read) {
            log.debug(
                    "Proofread window {} skipped: its focus holds the fingerprint {}",
                    question.window().label(),
                    question.fingerprint());
        }
        return read;
    }

    private static boolean holds(final @Nullable ProofreadVerdict verdict, final Question question) {
        return verdict != null
                && (verdict.fingerprint().equals(question.fingerprint())
                        || (question.round() == WindowFingerprint.ROUND_MARKED
                                && WindowFingerprint.isWholeBook(verdict.fingerprint())));
    }

    /**
     * Reads one window as one PROOFREAD step.
     *
     * @param question the non-null question, taken just before the call
     * @param reading the non-null reading, whose records are replaced by what the window commits
     * @param progress the non-null progress a pause reports
     * @return empty once the window was decided or left unread, or how a stop, a failure or a routed error ended it
     */
    Optional<RunEnd> read(final Question question, final WindowReading reading, final Supplier<JobProgress> progress) {
        final ProofreadWindow window = question.window();
        final String first = window.focusIds().getFirst();
        steps.started(StepKind.PROOFREAD, STEP_LABEL, question.key(), first);
        final Step<ProofreadCall.Answer> step = tools.routed()
                .untilAnsweredOrFlagged(
                        progress,
                        first,
                        new RoutedCalls.StepName(STEP_LABEL, question.key()),
                        () -> call(question, reading),
                        error -> unread(question, error));
        return switch (step) {
            case Step.Stopped<ProofreadCall.Answer>(final RunEnd end) -> {
                log.debug("Proofread window {} ended with the run state={}", window.label(), end.state());
                yield Optional.of(end);
            }
            case Step.Done<ProofreadCall.Answer>(final ProofreadCall.Answer answer) ->
                decided(question, answer, reading);
        };
    }

    private Result<ProofreadCall.Answer> call(final Question question, final WindowReading reading) {
        final ProofreadCall.@Nullable Planned planned = question.planned();
        return planned == null
                ? tools.call().read(question.window(), question.texts(), reading.map())
                : tools.call().send(planned, question.texts());
    }

    // Past its budget of pauses the window is left unread: its marks stay and go to the review desk.
    private static ProofreadCall.Answer unread(final Question question, final AppError error) {
        log.warn(
                "Proofread window {} left unread after its pause budget code={}; its marks stay for the desk",
                question.window().label(),
                error.code());
        return new ProofreadCall.Answer(question.window().focusIds(), Map.of(), false);
    }

    private Optional<RunEnd> decided(
            final Question question, final ProofreadCall.Answer answer, final WindowReading reading) {
        final ProofreadWindow window = question.window();
        if (!answer.readable()) {
            log.warn("Proofread window {} left unread: no readable reply", window.label());
            steps.ended(StepKind.PROOFREAD, question.key(), StepResult.FAILED);
            return Optional.empty();
        }
        final List<WindowVerdicts.Read> reads = reads(question, answer, reading);
        final WindowVerdicts.Decided decided =
                stamped(question, reading.verdicts().decide(reads, answer.changes(), question.fingerprint()), reading);
        final Optional<RunEnd> failed = committed(window, decided);
        if (failed.isPresent()) {
            return failed;
        }
        remember(question, decided, reading);
        emit.accept(WindowNotice.of(question, decided, reading.locators()));
        steps.ended(StepKind.PROOFREAD, question.key(), StepResult.DONE);
        log.info(
                "Proofread window decided round={} window={} focus={} verdicts={} proposals={} deferralsResolved={}",
                question.round(),
                window.label(),
                reads.size(),
                decided.counts(),
                decided.proposals().size(),
                decided.resolved().size());
        return Optional.empty();
    }

    private static List<WindowVerdicts.Read> reads(
            final Question question, final ProofreadCall.Answer answer, final WindowReading reading) {
        return answer.readIds().stream()
                .map(id -> new WindowVerdicts.Read(
                        reading.segment(id),
                        reading.record(id),
                        question.texts().current(id),
                        question.window().reasons().getOrDefault(id, List.of())))
                .toList();
    }

    // A segment whose text the window changed has its mark answered, so it no longer rides along in Max's second round.
    private static void remember(
            final Question question, final WindowVerdicts.Decided decided, final WindowReading reading) {
        decided.records().forEach(record -> {
            reading.records().put(record.segmentId(), record);
            if (!question.texts().current(record.segmentId()).equals(MarkList.maskedTarget(record))) {
                reading.keptNow().add(record.segmentId());
            }
        });
    }

    // A kept rewrite changes the window, so its verdicts carry the fingerprint of the window as it now stands: the
    // next Continue asks the same question and finds it answered.
    private WindowVerdicts.Decided stamped(
            final Question question, final WindowVerdicts.Decided decided, final WindowReading reading) {
        final Map<String, String> currents = new HashMap<>(question.texts().currents());
        decided.records()
                .forEach(record -> currents.put(
                        record.segmentId(), Objects.requireNonNull(MarkList.maskedTarget(record), record.segmentId())));
        if (currents.equals(question.texts().currents()) || question.planned() == null) {
            return decided;
        }
        final ProofreadCall.Texts after =
                new ProofreadCall.Texts(question.texts().sources(), currents);
        final Result<ProofreadCall.Planned> planned = tools.call().plan(question.window(), after, reading.map());
        final ProofreadCall.@Nullable Planned held = planned.data();
        if (held == null) {
            return decided;
        }
        final String now = WindowFingerprint.of(question.round(), question.window(), after, held.mapLines());
        log.debug(
                "Proofread window {} restamped after its kept rewrites",
                question.window().label());
        return new WindowVerdicts.Decided(
                decided.records().stream()
                        .map(record -> restamped(record, question.fingerprint(), now))
                        .toList(),
                decided.proposals(),
                decided.resolved(),
                decided.counts());
    }

    private static SegmentRecord restamped(final SegmentRecord record, final String was, final String now) {
        final @Nullable ProofreadVerdict verdict = record.proofread();
        if (verdict == null || !verdict.fingerprint().equals(was)) {
            return record;
        }
        return record.withProofread(new ProofreadVerdict(
                now, verdict.outcome(), verdict.reason(), verdict.before(), verdict.statusBefore()));
    }

    // The window's texts, verdicts, proposals and settled deferrals in one transaction.
    private Optional<RunEnd> committed(final ProofreadWindow window, final WindowVerdicts.Decided decided) {
        final Result<Integer> committed = stores.checkpoint()
                .commit(new ChunkCommit(
                        settings.projectId(),
                        decided.records(),
                        List.of(),
                        List.of(),
                        decided.proposals(),
                        decided.resolved(),
                        null));
        if (committed.isOk()) {
            return Optional.empty();
        }
        final AppError error = Objects.requireNonNull(committed.error(), "error");
        log.debug("Proofread window {} not committed code={}", window.label(), error.code());
        return Optional.of(new RunEnd(JobState.FAILED, error));
    }
}

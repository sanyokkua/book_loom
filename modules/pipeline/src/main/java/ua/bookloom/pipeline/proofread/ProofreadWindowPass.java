package ua.bookloom.pipeline.proofread;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.pipeline.JobProgress;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.ProofreadCoverage;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ChunkCommit;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.audit.FinalAudit;
import ua.bookloom.pipeline.context.BookMap;
import ua.bookloom.pipeline.run.RunEnd;
import ua.bookloom.pipeline.run.RunSettings;
import ua.bookloom.pipeline.run.RunSinks;
import ua.bookloom.pipeline.run.RunStores;
import ua.bookloom.pipeline.run.Step;

/**
 * The model part of the Proofread stage at Balanced and Max (design D15 "Windows"): one minimal-edit call per window of
 * marked and doubtful segments, in reading order, each window's texts, verdicts and settled deferrals committed in one
 * transaction before the next window is read. A stop asked for is honoured between windows; a call a pause or a stop
 * aborts, and a provider error, are routed as a draft's are, so a paused stage makes the window's call again on resume
 * and a stopped one leaves it undecided.
 *
 * <p>Used from the job thread only.
 */
@Slf4j
final class ProofreadWindowPass {

    /** The read rounds of a window; the marked windows are round 1 (design D15 "Resume"). */
    private static final int ROUND_MARKED = 1;

    private final ProofreadStage.Windows tools;
    private final RunSettings settings;
    private final RunSinks sinks;
    private final RunStores stores;

    /**
     * What the pass reads, as the code part left it.
     *
     * @param book the brief, the book and the map as they are now
     * @param records the stored records after the code part
     * @param open the open deferrals after the code part
     * @param marks the mark list in reading order
     */
    record State(MarkList.Book book, List<SegmentRecord> records, List<Deferral> open, List<Mark> marks) {}

    ProofreadWindowPass(
            final ProofreadStage.Windows tools,
            final RunSettings settings,
            final RunSinks sinks,
            final RunStores stores) {
        this.tools = Objects.requireNonNull(tools, "tools");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.sinks = Objects.requireNonNull(sinks, "sinks");
        this.stores = Objects.requireNonNull(stores, "stores");
    }

    /**
     * Reads the marked windows.
     *
     * @param state the non-null state the code part left
     * @param progress the non-null progress a pause reports
     * @return empty once every window was read, or how a stop, a failure or a routed error ended the run
     */
    Optional<RunEnd> run(final State state, final Supplier<JobProgress> progress) {
        final ProofreadCoverage coverage = settings.dial().proofreadCoverage();
        if (coverage == ProofreadCoverage.NONE) {
            log.debug("No proofread windows: the dial's coverage is {}", coverage);
            return Optional.empty();
        }
        final Map<String, SegmentRecord> records = new HashMap<>();
        state.records().forEach(record -> records.put(record.segmentId(), record));
        final Map<String, Segment> segments = new LinkedHashMap<>();
        MarkList.storySegments(state.book().document()).forEach(segment -> segments.put(segment.id(), segment));
        final Set<SegmentKind> kept = state.book().brief().alsoTranslate().keptKinds();
        final List<ProofreadWindow> windows = ProofreadWindows.of(
                state.marks(),
                List.copyOf(segments.keySet()),
                id -> records.containsKey(id) && MarkList.isListed(records.get(id), kept),
                tools.planner().profile().batchSize());
        log.info(
                "Proofread windows started project={} coverage={} windows={}",
                settings.projectId(),
                coverage,
                windows.size());
        final Reading reading = new Reading(state, records, segments, verdicts(state));
        for (final ProofreadWindow window : windows) {
            final Optional<RunEnd> end = window(window, reading, progress);
            if (end.isPresent()) {
                return end;
            }
        }
        return Optional.empty();
    }

    /** The pass's working state: the records as committed so far, the opened book's segments and the verdict rules. */
    private record Reading(
            State state, Map<String, SegmentRecord> records, Map<String, Segment> segments, WindowVerdicts verdicts) {

        // A window names only readable story segments, so each one has a record and a segment.
        SegmentRecord record(final String id) {
            return Objects.requireNonNull(records.get(id), () -> "no record for " + id);
        }

        Segment segment(final String id) {
            return Objects.requireNonNull(segments.get(id), () -> "no segment for " + id);
        }
    }

    private WindowVerdicts verdicts(final State state) {
        final BookBrief brief = state.book().brief();
        final String target = Objects.requireNonNull(brief.targetLanguage(), "target language");
        final ProofreadJudge judge = new ProofreadJudge(new ProofreadJudge.Book(
                settings.frame(),
                settings.names(),
                state.book().glossary(),
                new FinalAudit.Book(brief, state.book().document(), state.book().glossary(), tools.words()),
                tools.gate()));
        return new WindowVerdicts(
                judge,
                new WindowVerdicts.Facts(
                        settings.projectId(),
                        target,
                        MarkList.byTerm(state.book().glossary()),
                        brief.narrator(),
                        state.open()));
    }

    private Optional<RunEnd> window(
            final ProofreadWindow window, final Reading reading, final Supplier<JobProgress> progress) {
        if (sinks.boundaries().isStopRequested()) {
            log.info("Proofread stage stopped before window {} project={}", window.label(), settings.projectId());
            return Optional.of(new RunEnd(JobState.CANCELLED, null));
        }
        final ProofreadCall.Texts texts = texts(window, reading);
        final BookMap map = tools.planner()
                .map(reading.state().book().glossary(), reading.state().book().lexicon());
        final Step<ProofreadCall.Answer> step =
                tools.routed().untilAnswered(progress, null, () -> tools.call().read(window, texts, map));
        return switch (step) {
            case Step.Stopped<ProofreadCall.Answer>(final RunEnd end) -> {
                log.debug("Proofread window {} ended with the run state={}", window.label(), end.state());
                yield Optional.of(end);
            }
            case Step.Done<ProofreadCall.Answer>(final ProofreadCall.Answer answer) ->
                decided(window, texts, answer, reading);
        };
    }

    private ProofreadCall.Texts texts(final ProofreadWindow window, final Reading reading) {
        final Map<String, String> sources = new HashMap<>();
        final Map<String, String> currents = new HashMap<>();
        for (final String id : shownIds(window)) {
            sources.put(id, reading.segment(id).masked());
            currents.put(id, Objects.requireNonNull(MarkList.maskedTarget(reading.record(id)), id));
        }
        return new ProofreadCall.Texts(sources, currents);
    }

    // The focus, then the neighbours a window has.
    private static List<String> shownIds(final ProofreadWindow window) {
        final List<String> ids = new ArrayList<>(window.focusIds());
        final @Nullable String before = window.beforeId();
        final @Nullable String after = window.afterId();
        if (before != null) {
            ids.add(before);
        }
        if (after != null) {
            ids.add(after);
        }
        return ids;
    }

    private Optional<RunEnd> decided(
            final ProofreadWindow window,
            final ProofreadCall.Texts texts,
            final ProofreadCall.Answer answer,
            final Reading reading) {
        if (!answer.readable()) {
            log.warn("Proofread window {} left unread: its reply could not be read", window.label());
            return Optional.empty();
        }
        final List<WindowVerdicts.Read> reads = answer.readIds().stream()
                .map(id -> new WindowVerdicts.Read(
                        reading.segment(id),
                        reading.record(id),
                        texts.current(id),
                        window.reasons().getOrDefault(id, List.of())))
                .toList();
        final WindowVerdicts.Decided decided =
                reading.verdicts().decide(reads, answer.changes(), fingerprint(window, texts));
        final Optional<RunEnd> failed = committed(window, decided);
        if (failed.isPresent()) {
            return failed;
        }
        decided.records().forEach(record -> reading.records().put(record.segmentId(), record));
        log.info(
                "Proofread window decided window={} focus={} verdicts={} proposals={} deferralsResolved={}",
                window.label(),
                reads.size(),
                decided.counts(),
                decided.proposals().size(),
                decided.resolved().size());
        return Optional.empty();
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

    private static String fingerprint(final ProofreadWindow window, final ProofreadCall.Texts texts) {
        final List<String> parts = new ArrayList<>();
        window.focusIds().forEach(id -> {
            parts.add(texts.source(id));
            parts.add(texts.current(id));
        });
        final @Nullable String before = window.beforeId();
        final @Nullable String after = window.afterId();
        parts.add(before == null ? "" : texts.current(before));
        parts.add(after == null ? "" : texts.current(after));
        return WindowFingerprint.of(ROUND_MARKED, parts);
    }
}

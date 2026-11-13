package ua.bookloom.pipeline.proofread;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.pipeline.JobProgress;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.ProofreadCoverage;
import ua.bookloom.api.pipeline.ProofreadRoundStarted;
import ua.bookloom.api.pipeline.StepKind;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.audit.FinalAudit;
import ua.bookloom.pipeline.project.SegmentLocators;
import ua.bookloom.pipeline.run.RunEnd;
import ua.bookloom.pipeline.run.RunSettings;
import ua.bookloom.pipeline.run.RunSinks;
import ua.bookloom.pipeline.run.RunStores;

/**
 * The model part of the Proofread stage at Balanced and Max (design D15 "Windows", "Resume"): round 1 reads the marked
 * and doubtful windows; at Max, round 2 then reads every story paragraph in consecutive windows of the batch size, the
 * marks still open riding along as reasons. Each round first skips, with no call, every window already read with the
 * fingerprint it has now, plans one PROOFREAD step per window left, and reads those in reading order, each window
 * committed before the next is read. A stop asked for is honoured between windows.
 *
 * <p>Used from the job thread only.
 */
@Slf4j
final class ProofreadWindowPass {

    private final ProofreadStage.Windows tools;
    private final RunSettings settings;
    private final RunSinks sinks;
    private final WindowReader reader;
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
        this.reader = new WindowReader(tools, settings, stores, sinks.emit());
    }

    /**
     * Reads the windows the dial's coverage asks for.
     *
     * @param state the non-null state the code part left
     * @param progress the non-null progress a pause reports
     * @return empty once every window was read or skipped, or how a stop, a failure or a routed error ended the run
     */
    Optional<RunEnd> run(final State state, final Supplier<JobProgress> progress) {
        final ProofreadCoverage coverage = settings.dial().proofreadCoverage();
        if (coverage == ProofreadCoverage.NONE) {
            log.debug("No proofread windows: the dial's coverage is {}", coverage);
            return Optional.empty();
        }
        final WindowReading reading = reading(state);
        final Set<SegmentKind> kept = state.book().brief().alsoTranslate().keptKinds();
        final Layout layout = new Layout(
                List.copyOf(reading.segments().keySet()),
                MarkList.readable(reading.records(), kept),
                MarkList.focusable(reading.records(), kept),
                tools.planner().profile().batchSize());
        final Optional<RunEnd> marked = round(
                WindowFingerprint.ROUND_MARKED,
                ProofreadWindows.of(
                        state.marks(), layout.order(), layout.readable(), layout.focusable(), layout.batchSize()),
                layout,
                reading,
                progress);
        if (marked.isPresent() || coverage != ProofreadCoverage.MARKED_AND_DOUBTFUL_THEN_EVERY_PARAGRAPH) {
            return marked;
        }
        return round(
                WindowFingerprint.ROUND_WHOLE_BOOK,
                planned(WindowFingerprint.ROUND_WHOLE_BOOK, layout, reading),
                layout,
                reading,
                progress);
    }

    /** How the stage lays its windows out: the story's order, what a window may show and hold, and its size. */
    private record Layout(List<String> order, Predicate<String> readable, Predicate<String> focusable, int batchSize) {}

    // The windows a Continue would plan for the round from what the windows committed so far, as a stored project's
    // tally plans them: a mark a kept rewrite answered is gone, and a proposal or a settled deferral counts.
    private static List<ProofreadWindow> planned(final int round, final Layout layout, final WindowReading reading) {
        final List<Mark> marks = MarkList.of(
                reading.state().book(),
                List.copyOf(reading.records().values()),
                reading.verdicts().open());
        return round == WindowFingerprint.ROUND_MARKED
                ? ProofreadWindows.of(marks, layout.order(), layout.readable(), layout.focusable(), layout.batchSize())
                : ProofreadWindows.tiles(
                        layout.order(),
                        layout.readable(),
                        layout.focusable(),
                        layout.batchSize(),
                        MarkList.reasonsById(marks));
    }

    private WindowReading reading(final State state) {
        final Map<String, SegmentRecord> records = new HashMap<>();
        state.records().forEach(record -> records.put(record.segmentId(), record));
        final Map<String, Segment> segments = new LinkedHashMap<>();
        MarkList.storySegments(state.book().document()).forEach(segment -> segments.put(segment.id(), segment));
        return new WindowReading(
                state,
                records,
                segments,
                verdicts(state),
                tools.planner().map(state.book().glossary(), state.book().lexicon()),
                SegmentLocators.of(state.book().document()));
    }

    private Optional<RunEnd> round(
            final int round,
            final List<ProofreadWindow> windows,
            final Layout layout,
            final WindowReading reading,
            final Supplier<JobProgress> progress) {
        final List<ProofreadWindow> unread = windows.stream()
                .filter(window -> !reader.isRead(reader.question(round, window, reading), reading))
                .toList();
        started(round, windows, unread);
        sinks.emit().accept(new ProofreadRoundStarted(round, rounds(), windows.size(), unread.size()));
        tools.call().steps().planned(StepKind.PROOFREAD, unread.size(), false);
        final ReshapedWindows reshaped = new ReshapedWindows(round);
        windows.stream().filter(window -> !unread.contains(window)).forEach(reshaped::covered);
        for (final ProofreadWindow window : unread) {
            final Optional<RunEnd> end = read(round, window, reading, progress);
            if (end.isPresent()) {
                return end;
            }
            if (reading.isRead(round, window)) {
                reshaped.covered(window);
            }
        }
        log.info("Proofread round {} ended project={} read={}", round, settings.projectId(), unread.size());
        return reshaped.stamp(planned(round, layout, reading), reading, stores, settings.projectId());
    }

    private Optional<RunEnd> read(
            final int round,
            final ProofreadWindow window,
            final WindowReading reading,
            final Supplier<JobProgress> progress) {
        if (sinks.boundaries().isStopRequested()) {
            log.info("Proofread stage stopped before window {} project={}", window.label(), settings.projectId());
            return Optional.of(new RunEnd(JobState.CANCELLED, null));
        }
        return reader.read(reader.question(round, window, reading), reading, progress);
    }

    private int rounds() {
        return settings.dial().proofreadCoverage() == ProofreadCoverage.MARKED_AND_DOUBTFUL_THEN_EVERY_PARAGRAPH
                ? ProofreadRoundStarted.WHOLE_BOOK
                : 1;
    }

    private void started(final int round, final List<ProofreadWindow> windows, final List<ProofreadWindow> unread) {
        log.info(
                "Proofread round {} started project={} windows={} toRead={} skipped={}",
                round,
                settings.projectId(),
                windows.size(),
                unread.size(),
                windows.size() - unread.size());
        if (!unread.isEmpty() && unread.size() < windows.size()) {
            log.info(
                    "Proofread round {} continues at window {} of {}",
                    round,
                    windows.indexOf(unread.getFirst()) + 1,
                    windows.size());
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
}

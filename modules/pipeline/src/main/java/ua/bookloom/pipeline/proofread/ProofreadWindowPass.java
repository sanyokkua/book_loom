package ua.bookloom.pipeline.proofread;

import java.util.HashMap;
import java.util.HashSet;
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
        this.reader = new WindowReader(tools, settings, Objects.requireNonNull(stores, "stores"), sinks.emit());
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
        final List<String> order = List.copyOf(reading.segments().keySet());
        final Predicate<String> readable = readable(state, reading);
        final int batchSize = tools.planner().profile().batchSize();
        final Optional<RunEnd> marked = round(
                WindowFingerprint.ROUND_MARKED,
                ProofreadWindows.of(state.marks(), order, readable, batchSize),
                reading,
                progress);
        if (marked.isPresent() || coverage != ProofreadCoverage.MARKED_AND_DOUBTFUL_THEN_EVERY_PARAGRAPH) {
            return marked;
        }
        return round(
                WindowFingerprint.ROUND_WHOLE_BOOK,
                ProofreadWindows.tiles(order, readable, batchSize, openReasons(state, reading)),
                reading,
                progress);
    }

    private WindowReading reading(final State state) {
        final Map<String, SegmentRecord> records = new HashMap<>();
        state.records().forEach(record -> records.put(record.segmentId(), record));
        final Map<String, Segment> segments = new LinkedHashMap<>();
        MarkList.storySegments(state.book().document()).forEach(segment -> segments.put(segment.id(), segment));
        final Set<String> keptNow = new HashSet<>();
        return new WindowReading(
                state,
                records,
                segments,
                verdicts(state),
                tools.planner().map(state.book().glossary(), state.book().lexicon()),
                keptNow,
                SegmentLocators.of(state.book().document()));
    }

    private static Predicate<String> readable(final State state, final WindowReading reading) {
        final Set<SegmentKind> kept = state.book().brief().alsoTranslate().keptKinds();
        return id -> reading.records().containsKey(id)
                && MarkList.isListed(reading.records().get(id), kept);
    }

    // The marks still open after round 1: a segment whose text round 1 changed has had its mark answered.
    private static Map<String, List<String>> openReasons(final State state, final WindowReading reading) {
        final Map<String, List<String>> reasons = new HashMap<>();
        state.marks().stream()
                .filter(mark -> !reading.keptNow().contains(mark.segmentId()))
                .forEach(mark -> reasons.put(mark.segmentId(), mark.reasons()));
        return reasons;
    }

    private Optional<RunEnd> round(
            final int round,
            final List<ProofreadWindow> windows,
            final WindowReading reading,
            final Supplier<JobProgress> progress) {
        final List<ProofreadWindow> unread = windows.stream()
                .filter(window -> !reader.isRead(reader.question(round, window, reading), reading))
                .toList();
        started(round, windows, unread);
        sinks.emit().accept(new ProofreadRoundStarted(round, rounds(), windows.size(), unread.size()));
        tools.call().steps().planned(StepKind.PROOFREAD, unread.size(), false);
        for (final ProofreadWindow window : unread) {
            if (sinks.boundaries().isStopRequested()) {
                log.info("Proofread stage stopped before window {} project={}", window.label(), settings.projectId());
                return Optional.of(new RunEnd(JobState.CANCELLED, null));
            }
            final Optional<RunEnd> end = reader.read(reader.question(round, window, reading), reading, progress);
            if (end.isPresent()) {
                return end;
            }
        }
        log.info("Proofread round {} ended project={} read={}", round, settings.projectId(), unread.size());
        return Optional.empty();
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

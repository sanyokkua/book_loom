package ua.bookloom.pipeline.eval;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.SequenceJobs;
import ua.bookloom.pipeline.SequenceJobs.Prepared;
import ua.bookloom.pipeline.eval.MiniBook.Block;
import ua.bookloom.pipeline.eval.MiniRun.Calls;
import ua.bookloom.pipeline.eval.MiniRun.Outcome;
import ua.bookloom.pipeline.revision.PassOptions;

/**
 * Runs the mini book the way the app does a whole book: the autonomous names pass, the translation job, then the
 * export's consistency pass, all through the production classes over one imported project and one model. The brief
 * names a first-person male narrator, as the book is told by one.
 */
@Slf4j
final class MiniRunner {

    private static final Pattern TOKEN = Pattern.compile("⟦[^⟧]*⟧");
    private static final String SOURCE = "en";
    private static final String TARGET = "uk";

    private final MiniBook book;
    private final QualityDial dial;
    private final NamePolicy policy;
    private final @Nullable Integer window;

    /**
     * Creates the runner.
     *
     * @param book the book to translate
     * @param dial the quality dial of the brief
     * @param policy the brief's name policy
     * @param window the context window the provider is taken to report, or null for the run's default
     */
    MiniRunner(final MiniBook book, final QualityDial dial, final NamePolicy policy, @Nullable final Integer window) {
        this.book = Objects.requireNonNull(book, "book");
        this.dial = Objects.requireNonNull(dial, "dial");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.window = window;
    }

    /**
     * Runs the book.
     *
     * @param model the model every phase calls
     * @param workDir an existing directory the EPUB is written into
     * @return what the run decided
     */
    MiniRun run(final ChatModel model, final Path workDir) {
        log.info("Mini eval start dial={} names={} window={}", dial, policy, window);
        final Path file = book.writeEpub(workDir);
        final Narrator narrator = new Narrator(NarratorPerson.FIRST, Gender.MALE);
        final Prepared prepared =
                SequenceJobs.importBook(file, SequenceJobs.brief(SOURCE, TARGET, dial, narrator, policy));
        final StageProbe probe = new StageProbe(model);
        final List<String> errors = new ArrayList<>();
        final Instant started = Instant.now();
        final int names = namesPass(prepared, probe, errors);
        final int afterNames = probe.mark();
        final JobState end = translate(prepared, probe, errors);
        final int afterJob = probe.mark();
        consistency(prepared, probe, errors);
        final Calls calls = new Calls(names, afterJob - afterNames, probe.mark() - afterJob);
        final Duration elapsed = Duration.between(started, Instant.now());
        log.info("Mini eval end state={} calls={} errors={} elapsed={}", end, calls, errors, elapsed);
        return new MiniRun(policy, end, outcomes(prepared), glossary(prepared), errors, calls, elapsed);
    }

    private int namesPass(final Prepared prepared, final StageProbe probe, final List<String> errors) {
        final Result<?> result = SequenceJobs.namesPass(prepared).run(prepared.projectId(), probe, window, event -> {});
        note("names pass", result, errors);
        return probe.mark();
    }

    private JobState translate(final Prepared prepared, final StageProbe probe, final List<String> errors) {
        final Result<JobReport> result = SequenceJobs.run(prepared, probe, ReviewMode.UNATTENDED, window, event -> {});
        note("translation", result, errors);
        return result.isOk() ? Objects.requireNonNull(result.data()).end() : JobState.FAILED;
    }

    private void consistency(final Prepared prepared, final StageProbe probe, final List<String> errors) {
        final Result<?> result = SequenceJobs.consistencyPass(prepared)
                .run(prepared.projectId(), probe.calls(TARGET), new PassOptions(true, dial == QualityDial.MAX));
        note("consistency pass", result, errors);
    }

    private static void note(final String phase, final Result<?> result, final List<String> errors) {
        if (result.isErr()) {
            errors.add(phase + ": " + Objects.requireNonNull(result.error()).code());
            log.warn("Mini eval {} ended in {}", phase, result.error().code());
        }
    }

    private Map<String, Outcome> outcomes(final Prepared prepared) {
        final Map<String, SegmentRecord> stored = new LinkedHashMap<>();
        Objects.requireNonNull(
                        prepared.stores().segments().all(prepared.projectId()).data())
                .forEach(record -> stored.put(record.segmentId(), record));
        final Map<String, Block> byText = new LinkedHashMap<>();
        book.blocks().forEach(block -> byText.put(block.text(), block));
        final Map<String, Outcome> outcomes = new LinkedHashMap<>();
        for (final Unit unit : prepared.document().units()) {
            for (final Segment segment : unit.segments()) {
                final Block block = byText.get(
                        TOKEN.matcher(segment.masked()).replaceAll("").strip());
                final SegmentRecord record = stored.get(segment.id());
                if (block != null && record != null) {
                    outcomes.put(block.id(), outcome(block, record));
                }
            }
        }
        return outcomes;
    }

    private static Outcome outcome(final Block block, final SegmentRecord record) {
        final String target = record.maskedMachineTarget();
        return new Outcome(
                block.id(),
                block.text(),
                target == null ? null : TOKEN.matcher(target).replaceAll(""),
                record.status(),
                record.findings());
    }

    private static List<GlossaryEntry> glossary(final Prepared prepared) {
        return Objects.requireNonNull(
                prepared.stores().glossary().all(prepared.projectId()).data());
    }
}

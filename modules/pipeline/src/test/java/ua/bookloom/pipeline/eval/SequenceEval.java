package ua.bookloom.pipeline.eval;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.ModelCallFinished;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.project.AppliedEdit;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.NarratorHint;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.Project;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.SequenceJobs;
import ua.bookloom.pipeline.SequenceJobs.Prepared;
import ua.bookloom.pipeline.eval.SequenceFixture.Chapter;
import ua.bookloom.pipeline.eval.SequenceFixture.GlossarySeed;
import ua.bookloom.pipeline.eval.SequenceRun.Decided;
import ua.bookloom.pipeline.glossary.GlossaryIds;
import ua.bookloom.pipeline.narrator.NarratorDetector;

/**
 * The sequence eval (15e.3): the fixture book through the real batched job — preparation, batches, reviewer, repair path,
 * lexicon and learner, summary — against whatever {@link ChatModel} it is given, then a read of what the run decided.
 * Every case of the other suites stands alone; the defects of a long run (a title that drifts between renderings, a
 * name in two spellings, a narrator's gender that slips) only exist over many segments with the context accumulating.
 */
@Slf4j
final class SequenceEval {

    private static final Pattern TOKEN = Pattern.compile("⟦[^⟧]*⟧");

    private final SequenceFixture fixture;
    private final QualityDial dial;
    private final @Nullable Integer window;
    private final SequenceNarratorMode narrator;
    private final Gender detectedGender;

    /**
     * Creates the eval.
     *
     * @param fixture the book and what is known about it
     * @param dial the quality dial the run is made on
     * @param window the context window the provider is taken to report, or null for the run's default
     * @param narrator what the brief says about the narrator
     */
    SequenceEval(
            final SequenceFixture fixture,
            final QualityDial dial,
            @Nullable final Integer window,
            final SequenceNarratorMode narrator) {
        this(fixture, dial, window, narrator, Gender.MALE);
    }

    /**
     * Creates the eval with the answer the Start question gets when the mode is {@code detect}.
     *
     * @param detectedGender the gender a detected first-person narrator is given; ignored in the other modes
     */
    SequenceEval(
            final SequenceFixture fixture,
            final QualityDial dial,
            @Nullable final Integer window,
            final SequenceNarratorMode narrator,
            final Gender detectedGender) {
        this.detectedGender = Objects.requireNonNull(detectedGender, "detectedGender");
        this.fixture = Objects.requireNonNull(fixture, "fixture");
        this.dial = Objects.requireNonNull(dial, "dial");
        this.window = window;
        this.narrator = Objects.requireNonNull(narrator, "narrator");
    }

    /**
     * Runs the fixture unattended.
     *
     * @param model the model the job calls
     * @param workDir a directory the book is written into; its name keeps the segment ids unique to this run
     * @return what the run decided
     */
    SequenceRun run(final ChatModel model, final Path workDir) {
        final String name = "sequence-" + workDir.getFileName() + ".md";
        final Path book = write(workDir.resolve(name));
        final BookBrief brief = SequenceJobs.brief(fixture.source(), fixture.target(), dial, narrator.narrator());
        final Prepared prepared = SequenceJobs.importBook(book, brief);
        seedGlossary(prepared);
        applyDetectedNarrator(prepared);
        log.info(
                "Sequence eval start project={} dial={} window={} narrator={}",
                prepared.projectId(),
                dial,
                window,
                narrator.label());
        try (SequenceLogCapture capture = new SequenceLogCapture()) {
            return execute(prepared, model, capture);
        }
    }

    private SequenceRun execute(final Prepared prepared, final ChatModel model, final SequenceLogCapture capture) {
        final List<ModelCallFinished> calls = new CopyOnWriteArrayList<>();
        final SequenceFinishRecorder recorder = new SequenceFinishRecorder(model);
        final Instant started = Instant.now();
        final JobReport report = Objects.requireNonNull(
                SequenceJobs.run(prepared, recorder, ReviewMode.UNATTENDED, window, event -> {
                            if (event instanceof ModelCallFinished finished) {
                                calls.add(finished);
                            }
                        })
                        .data(),
                "job report");
        final Duration elapsed = Duration.between(started, Instant.now());
        final List<Decided> segments = decided(prepared);
        log.info("Sequence eval end state={} segments={} elapsed={}", report.end(), segments.size(), elapsed);
        return result(prepared, capture, new SequenceRun.Outcome(report, elapsed, calls, segments), recorder);
    }

    private SequenceRun result(
            final Prepared prepared,
            final SequenceLogCapture capture,
            final SequenceRun.Outcome outcome,
            final SequenceFinishRecorder recorder) {
        final Set<String> ids = outcome.segments().stream().map(Decided::id).collect(Collectors.toSet());
        final List<LexiconEntry> lexicon = Objects.requireNonNull(
                prepared.stores().lexicon().all(prepared.projectId()).data());
        final Set<String> failedFirst = capture.hardGateFailuresRound0(ids);
        return new SequenceRun(
                outcome.report(),
                outcome.elapsed(),
                outcome.calls(),
                outcome.segments(),
                lexicon,
                capture.fallbackReasons(ids),
                capture.editsRefused(ids),
                failedFirst.size(),
                capture.firstRepairKinds(failedFirst),
                recorder.reviewerTruncated(),
                narrator);
    }

    private static Path write(final Path file) {
        try {
            return Files.writeString(file, SequenceFixture.bookText(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // What the Start question does in the application: a detected first-person narration plus the person's answer.
    void applyDetectedNarrator(final Prepared prepared) {
        if (narrator != SequenceNarratorMode.DETECT) {
            return;
        }
        final Optional<NarratorHint> hint = NarratorDetector.detect(prepared.document(), fixture.source());
        log.info("Sequence eval narrator hint={} answer={}", hint.orElse(null), detectedGender);
        if (hint.isEmpty() || !hint.get().suggestsFirstPerson()) {
            return;
        }
        final Project project = Objects.requireNonNull(
                        prepared.stores().projects().find(prepared.projectId()).data())
                .orElseThrow();
        prepared.stores()
                .projects()
                .save(project.withBrief(
                        project.brief().withNarrator(new Narrator(NarratorPerson.FIRST, detectedGender))));
    }

    private void seedGlossary(final Prepared prepared) {
        for (final GlossarySeed seed : fixture.glossary()) {
            prepared.stores()
                    .glossary()
                    .add(new GlossaryEntry(
                            GlossaryIds.of(prepared.projectId(), seed.term()),
                            prepared.projectId(),
                            seed.term(),
                            seed.target(),
                            seed.type(),
                            seed.gender(),
                            seed.locked()));
        }
    }

    private List<Decided> decided(final Prepared prepared) {
        final Map<String, SegmentRecord> stored =
                Objects.requireNonNull(prepared.stores()
                                .segments()
                                .all(prepared.projectId())
                                .data())
                        .stream()
                        .collect(Collectors.toMap(SegmentRecord::segmentId, record -> record));
        final List<Decided> decided = new ArrayList<>();
        int chapter = -1;
        for (final Unit unit : prepared.document().units()) {
            for (final Segment segment : unit.segments()) {
                chapter = chapterOf(segment, chapter);
                final SegmentRecord record = stored.get(segment.id());
                if (record != null) {
                    decided.add(decide(segment, record, chapter));
                }
            }
        }
        return decided;
    }

    private int chapterOf(final Segment segment, final int current) {
        final List<Chapter> chapters = fixture.chapters();
        for (int i = 0; i < chapters.size(); i++) {
            if (segment.sourceInner().strip().equalsIgnoreCase(chapters.get(i).title())) {
                return i;
            }
        }
        return current;
    }

    private Decided decide(final Segment segment, final SegmentRecord record, final int chapter) {
        final String target = record.maskedMachineTarget();
        return new Decided(
                segment.id(),
                chapter,
                chapter < 0
                        ? NarratorPerson.UNSPECIFIED
                        : fixture.chapters().get(chapter).narrator(),
                TOKEN.matcher(segment.masked()).replaceAll(""),
                target == null ? null : TOKEN.matcher(target).replaceAll(""),
                record.status(),
                record.path(),
                (int) record.findings().stream()
                        .filter(finding -> AppliedEdit.from(finding).isPresent())
                        .count());
    }
}

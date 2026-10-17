package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.pipeline.BriefSuggestion;
import ua.bookloom.api.pipeline.ExportJob;
import ua.bookloom.api.pipeline.ExportReport;
import ua.bookloom.api.pipeline.ExportRequest;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.SideFile;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.pipeline.SequenceJobs;
import ua.bookloom.pipeline.SequenceJobs.Prepared;
import ua.bookloom.pipeline.eval.BookRunSummary.BriefChoice;
import ua.bookloom.pipeline.eval.BookRunSummary.Calls;
import ua.bookloom.pipeline.eval.BookRunSummary.Written;

/**
 * Runs one existing book the way a typical user does in the window, through the production classes over one imported
 * project and one model: the predicted Book Brief is accepted (the name policy and everything it does not suggest stay
 * at the defaults), then the autonomous names pass, the Start re-check of the narrator, the translation job at the
 * dial, and the export written twice, once with the consistency pass off and once with it on. Both exports carry the
 * glossary CSV and the quality report.
 */
@Slf4j
final class BookRunner {

    private static final String NO_PASS = "no_consistency_pass";
    private static final String WITH_PASS = "with_consistency_pass";
    private static final double MS_PER_MINUTE = 60_000.0;
    private static final Set<SideFile> SIDE_FILES = Set.of(SideFile.GLOSSARY_CSV, SideFile.QUALITY_REPORT);

    /**
     * What to run.
     *
     * @param book the book file, in any of the four formats
     * @param outDir an existing directory the exports, the CSV and the report land in
     * @param sourceLanguage the source language tag
     * @param targetLanguage the target language tag
     * @param dial the quality dial of the brief
     * @param window the context window the provider is taken to report, or null for the run's default
     */
    record Options(
            Path book,
            Path outDir,
            String sourceLanguage,
            String targetLanguage,
            QualityDial dial,
            @Nullable Integer window) {

        /** Rejects a missing part. */
        Options {
            Objects.requireNonNull(book, "book");
            Objects.requireNonNull(outDir, "outDir");
            Objects.requireNonNull(sourceLanguage, "sourceLanguage");
            Objects.requireNonNull(targetLanguage, "targetLanguage");
            Objects.requireNonNull(dial, "dial");
        }
    }

    private final Options options;

    BookRunner(final Options options) {
        this.options = Objects.requireNonNull(options, "options");
    }

    /**
     * Runs the book and writes {@code book-run.json} beside the exports.
     *
     * @param model the model every stage calls
     * @param modelName the model's name, for the report
     * @return what the run decided and measured
     */
    BookRunSummary run(final ChatModel model, final String modelName) {
        final Prepared prepared = importBook();
        final StageProbe probe = new StageProbe(model);
        final List<String> errors = new ArrayList<>();
        final Instant started = Instant.now();
        final boolean suggested = acceptBrief(prepared, probe, errors);
        final int afterBrief = probe.mark();
        log.info("Book run stage: names pass");
        final int afterNames = WholeBookStages.namesPass(prepared, probe, options.window(), errors);
        recheckNarrator(prepared, errors);
        log.info("Book run stage: translation");
        final JobState end = WholeBookStages.translate(prepared, probe, options.window(), errors);
        final int afterJob = probe.mark();
        log.info("Book run stage: exports");
        final Optional<Written> without = export(prepared, NO_PASS, false, probe, errors);
        final Optional<Written> with = export(prepared, WITH_PASS, true, probe, errors);
        final Calls calls =
                new Calls(afterBrief, afterNames - afterBrief, afterJob - afterNames, probe.mark() - afterJob);
        final BookRunSummary summary =
                summary(prepared, modelName, suggested, end, calls, started, without, with, errors);
        write(summary);
        log.info("Book run end state={} calls={} minutes={} errors={}", end, calls, summary.minutes(), errors);
        return summary;
    }

    private Prepared importBook() {
        log.info(
                "Book run start book={} dial={} source={} target={} window={}",
                options.book().getFileName(),
                options.dial(),
                options.sourceLanguage(),
                options.targetLanguage(),
                options.window());
        final BookBrief initial = BookBrief.defaults(options.sourceLanguage())
                .withLanguages(options.sourceLanguage(), options.targetLanguage())
                .withDial(options.dial());
        return SequenceJobs.importBook(options.book(), initial);
    }

    // The window writes the model's suggestion into the brief when it arrives; a failed suggestion leaves the defaults.
    private boolean acceptBrief(final Prepared prepared, final StageProbe probe, final List<String> errors) {
        log.info("Book run stage: brief suggestion");
        final Result<BriefSuggestion> answer = JudgementRunner.assistant(prepared)
                .suggestBrief(prepared.projectId(), probe, options.window(), event -> {});
        WholeBookStages.note("brief suggestion", answer, errors);
        if (answer.isErr()) {
            return false;
        }
        final BookBrief accepted = BriefAcceptance.accept(
                SequenceJobs.storedBrief(prepared), Objects.requireNonNull(answer.data(), "suggestion"));
        SequenceJobs.saveBrief(prepared, accepted);
        return true;
    }

    // Start checks the narrator's pending quotes against the glossary the names pass just wrote.
    private void recheckNarrator(final Prepared prepared, final List<String> errors) {
        final Result<Narrator> checked = JudgementRunner.assistant(prepared).recheckNarrator(prepared.projectId());
        WholeBookStages.note("narrator re-check", checked, errors);
        if (checked.isOk()) {
            final BookBrief brief = SequenceJobs.storedBrief(prepared);
            final Narrator narrator = Objects.requireNonNull(checked.data(), "narrator");
            if (!narrator.equals(brief.narrator())) {
                log.debug("Narrator re-check changed the narrator to {}", narrator.gender());
                SequenceJobs.saveBrief(prepared, brief.withNarrator(narrator));
            }
        }
    }

    private Optional<Written> export(
            final Prepared prepared,
            final String label,
            final boolean consistencyPass,
            final StageProbe probe,
            final List<String> errors) {
        final Path destination = options.outDir().resolve(stem() + "." + label + "." + extension());
        log.debug("Export {} to {} consistencyPass={}", label, destination, consistencyPass);
        final ExportRequest request =
                new ExportRequest(prepared.projectId(), destination, true, SIDE_FILES, consistencyPass);
        final Result<ExportJob> job =
                SequenceJobs.exportService(prepared).newExport(request, consistencyPass ? probe : null);
        WholeBookStages.note("export " + label, job, errors);
        if (job.isErr()) {
            return Optional.empty();
        }
        final Result<ExportReport> done =
                Objects.requireNonNull(job.data(), "job").run();
        WholeBookStages.note("export " + label, done, errors);
        return done.isErr() ? Optional.empty() : Optional.of(counts(prepared, Objects.requireNonNull(done.data())));
    }

    private Written counts(final Prepared prepared, final ExportReport report) {
        final DocumentPort documents =
                Guice.createInjector(new DocumentModule()).getInstance(DocumentPort.class);
        return BookRunCounts.written(
                documents,
                options.book(),
                report.destination(),
                report,
                options.sourceLanguage(),
                options.targetLanguage(),
                SequenceJobs.storedBrief(prepared).narrator());
    }

    private BookRunSummary summary(
            final Prepared prepared,
            final String modelName,
            final boolean suggested,
            final JobState end,
            final Calls calls,
            final Instant started,
            final Optional<Written> without,
            final Optional<Written> with,
            final List<String> errors) {
        final List<SegmentRecord> records = Objects.requireNonNull(
                prepared.stores().segments().all(prepared.projectId()).data());
        final List<GlossaryEntry> glossary = Objects.requireNonNull(
                prepared.stores().glossary().all(prepared.projectId()).data());
        final double minutes = Duration.between(started, Instant.now()).toMillis() / MS_PER_MINUTE;
        return new BookRunSummary(
                options.book().getFileName().toString(),
                modelName,
                options.dial().name(),
                choice(SequenceJobs.storedBrief(prepared), suggested),
                end.name(),
                calls,
                Math.round(minutes * 100) / 100.0,
                records.size(),
                (int) records.stream()
                        .filter(r -> r.status() == SegmentStatus.FLAGGED)
                        .count(),
                BookRunCounts.characters(glossary),
                without.orElse(null),
                with.orElse(null),
                errors);
    }

    private BriefChoice choice(final BookBrief brief, final boolean suggested) {
        return new BriefChoice(
                suggested,
                options.sourceLanguage(),
                options.targetLanguage(),
                brief.genre(),
                brief.register().name(),
                brief.voiceEra(),
                brief.audience(),
                brief.names().name(),
                brief.narrator().person().name(),
                brief.narrator().gender().name(),
                brief.narrator().genderOrigin().name());
    }

    private void write(final BookRunSummary summary) {
        try {
            new ObjectMapper()
                    .writerWithDefaultPrettyPrinter()
                    .writeValue(options.outDir().resolve(BookRunSummary.FILE).toFile(), summary);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // The book's name without its format ending, so "a.b.fb2.zip" becomes "a.b".
    private String stem() {
        final String name = options.book().getFileName().toString();
        return name.substring(0, name.length() - extension().length() - 1);
    }

    private String extension() {
        final String name = options.book().getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".fb2.zip") ? "fb2.zip" : name.substring(name.lastIndexOf('.') + 1);
    }
}

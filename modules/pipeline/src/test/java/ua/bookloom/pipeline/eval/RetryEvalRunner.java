package ua.bookloom.pipeline.eval;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.SequenceJobs;
import ua.bookloom.pipeline.SequenceJobs.Prepared;
import ua.bookloom.pipeline.eval.StageCases.Filler;
import ua.bookloom.pipeline.eval.StageCases.RetryBook;
import ua.bookloom.pipeline.eval.StageCases.RetryCase;
import ua.bookloom.pipeline.glossary.GlossaryIds;

/**
 * Plants a book's defective drafts through the real job (see {@link PlantedDrafts}), then hands the stored, decided
 * book to the production repair stages: {@code retry} runs the review desk's {@code RetryDraft.retry} per case.
 * A case passes when the final text is usable (accepted or revised), no longer matches the case's defect pattern and
 * still matches its required one.
 */
@Slf4j
final class RetryEvalRunner {

    private static final Set<SegmentStatus> USABLE = Set.of(SegmentStatus.ACCEPTED, SegmentStatus.REVISED);

    // A BOM keeps the importer's charset ladder from reading a file with a couple of curly quotes as windows-1252.
    private static final String BOM = "\uFEFF";

    private final StageProbe probe;
    private final Path workDir;

    /** A planted book: the stored project and the segment id of each case's source text. */
    private record Planted(Prepared prepared, Map<String, String> idByText) {}

    RetryEvalRunner(final ChatModel model, final Path workDir) {
        this.probe = new StageProbe(Objects.requireNonNull(model, "model"));
        this.workDir = Objects.requireNonNull(workDir, "workDir");
    }

    StageProbe probe() {
        return probe;
    }

    List<StageRow> retry(final List<RetryBook> books) {
        final List<StageRow> rows = new ArrayList<>();
        for (final RetryBook book : books) {
            final Planted planted = plant(book, "retry");
            for (final RetryCase retryCase : book.cases()) {
                final int mark = probe.mark();
                final Result<SegmentRecord> result = SequenceJobs.retryDraft(planted.prepared())
                        .retry(
                                planted.prepared().projectId(),
                                planted.idByText().get(retryCase.text()),
                                null,
                                false,
                                probe);
                rows.add(retryRow(book, retryCase, result, probe.since(mark)));
            }
        }
        return rows;
    }

    private static StageRow retryRow(
            final RetryBook book,
            final RetryCase retryCase,
            final Result<SegmentRecord> result,
            final StageProbe.Counts counts) {
        final String id = book.id() + ":" + retryCase.id();
        if (result.isErr()) {
            return new StageRow(
                    id,
                    false,
                    "error " + Objects.requireNonNull(result.error()).code(),
                    counts.calls(),
                    counts.failed(),
                    counts.repeated(),
                    0);
        }
        return verdict(id, retryCase, result.data(), counts);
    }

    private static StageRow verdict(
            final String id,
            final RetryCase retryCase,
            @Nullable final SegmentRecord record,
            final StageProbe.Counts counts) {
        if (record == null) {
            return new StageRow(id, false, "no stored record", counts.calls(), counts.failed(), counts.repeated(), 0);
        }
        final String text = record.userTarget() != null ? record.userTarget() : record.machineTarget();
        final List<String> misses = new ArrayList<>();
        if (!USABLE.contains(record.status()) || text == null) {
            misses.add("no usable text");
        }
        if (text != null
                && retryCase.defect() != null
                && Pattern.compile(retryCase.defect()).matcher(text).find()) {
            misses.add("defect remains");
        }
        if (retryCase.required() != null
                && (text == null
                        || !Pattern.compile(retryCase.required()).matcher(text).find())) {
            misses.add("required text missing");
        }
        final String detail = record.status() + (misses.isEmpty() ? "" : ": " + String.join(", ", misses));
        return new StageRow(id, misses.isEmpty(), detail, counts.calls(), counts.failed(), counts.repeated(), 0);
    }

    private Planted plant(final RetryBook book, final String suite) {
        final Map<String, String> drafts = new LinkedHashMap<>();
        final List<String> paragraphs = new ArrayList<>();
        for (int i = 0; i < Math.max(book.fillers().size(), book.cases().size()); i++) {
            if (i < book.fillers().size()) {
                final Filler filler = book.fillers().get(i);
                paragraphs.add(filler.text());
                drafts.put(filler.text(), filler.draft());
            }
            if (i < book.cases().size()) {
                paragraphs.add(book.cases().get(i).text());
                drafts.put(book.cases().get(i).text(), book.cases().get(i).draft());
            }
        }
        final Prepared prepared = SequenceJobs.importBook(
                write(book, suite, paragraphs),
                SequenceJobs.brief(book.source(), book.target(), QualityDial.FAST, Narrator.unspecified()));
        seedGlossary(prepared, book);
        log.info("Retry eval plants {} paragraphs of {} for {}", paragraphs.size(), book.id(), suite);
        SequenceJobs.run(prepared, new PlantedDrafts(drafts), ReviewMode.UNATTENDED, null, event -> {});
        final Map<String, String> ids = new LinkedHashMap<>();
        prepared.document()
                .units()
                .forEach(unit -> unit.segments().forEach(segment -> ids.put(segment.sourceInner(), segment.id())));
        return new Planted(prepared, ids);
    }

    // Unlocked: a locked name is hidden behind a token in the source the model is shown, and the planted drafts are
    // matched by the source's text. The vocative check reads the name and its rendering all the same.
    private static void seedGlossary(final Prepared prepared, final RetryBook book) {
        final String project = prepared.projectId();
        book.glossary()
                .forEach(seed -> prepared.stores()
                        .glossary()
                        .add(GlossaryEntry.of(
                                GlossaryIds.of(project, seed.term()),
                                project,
                                seed.term(),
                                seed.target(),
                                TermType.CHARACTER,
                                Gender.FEMALE,
                                false)));
    }

    private Path write(final RetryBook book, final String suite, final List<String> paragraphs) {
        try {
            return Files.writeString(
                    workDir.resolve("retry-" + suite + "-" + book.id() + ".md"),
                    BOM + String.join("\n\n", paragraphs) + "\n",
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

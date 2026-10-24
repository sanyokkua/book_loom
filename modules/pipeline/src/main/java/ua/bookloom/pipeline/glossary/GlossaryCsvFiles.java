package ua.bookloom.pipeline.glossary;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.FailureReason;
import ua.bookloom.api.Result;
import ua.bookloom.api.SafeDetails;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.pipeline.GlossaryImportReport;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.util.files.FileFailures;
import ua.bookloom.util.files.FileWrites;
import ua.bookloom.util.text.GlossaryKeys;

/**
 * Reads and writes the glossary file for the glossary service: UTF-8 only, a header in any column order, one row per
 * term (the last one wins), the whole import or none of it, and an export that never leaves a half-written file.
 *
 * @param glossary where the rows are stored
 * @param apply stores one row's entry, with the service's own rules for an added or a changed entry
 */
@Slf4j
record GlossaryCsvFiles(GlossaryRepository glossary, Function<GlossaryCsv.Row, Result<Boolean>> apply) {

    GlossaryCsvFiles {
        Objects.requireNonNull(glossary, "glossary");
        Objects.requireNonNull(apply, "apply");
    }

    Result<GlossaryImportReport> importFrom(final String projectId, final Path source) {
        final Result<String> text = readUtf8(projectId, source);
        if (text.isErr()) {
            return Result.err(Objects.requireNonNull(text.error(), "error"));
        }
        final GlossaryCsv.Parsed parsed = GlossaryCsv.parse(Objects.requireNonNull(text.data(), "text"));
        if (parsed.headerRefused()) {
            return Result.err(fileError(
                    FailureReason.CSV_HEADER,
                    "The glossary file's header is not usable",
                    "The first line must name the columns term, target, type, gender and locked, each once."));
        }
        final List<GlossaryCsv.Row> rows = lastOfEachTerm(parsed.rows());
        final int duplicates = parsed.rows().size() - rows.size();
        log.debug("Glossary import project={} rows={} duplicates={}", projectId, rows.size(), duplicates);
        final Result<List<GlossaryEntry>> before = glossary.all(projectId);
        if (before.isErr()) {
            return Result.err(Objects.requireNonNull(before.error(), "error"));
        }
        final Result<GlossaryImportReport> report = applyAll(rows, parsed.malformedLines(), duplicates);
        if (report.isErr()) {
            log.warn("Glossary import project={} failed half-way: the glossary is put back as it was", projectId);
            GlossaryRestore.restore(glossary, projectId, Objects.requireNonNull(before.data(), "before"));
        }
        return report;
    }

    Result<Path> exportTo(final String projectId, final Path destination) {
        final Result<List<GlossaryEntry>> held = glossary.all(projectId);
        if (held.isErr()) {
            return Result.err(Objects.requireNonNull(held.error(), "error"));
        }
        final List<GlossaryEntry> entries = Objects.requireNonNull(held.data(), "held");
        try {
            FileWrites.replace(destination, GlossaryCsv.format(entries));
        } catch (UncheckedIOException failure) {
            return Result.err(exportError(projectId, destination, failure));
        }
        log.info("Glossary exported project={} path={} entries={}", projectId, destination, entries.size());
        return Result.ok(destination);
    }

    private Result<GlossaryImportReport> applyAll(
            final List<GlossaryCsv.Row> rows, final List<Integer> malformed, final int duplicates) {
        final List<Integer> refused = new ArrayList<>();
        int imported = 0;
        for (final GlossaryCsv.Row row : rows) {
            final Result<Boolean> applied = apply.apply(row);
            if (applied.isErr()) {
                return Result.err(Objects.requireNonNull(applied.error(), "error"));
            }
            if (Boolean.TRUE.equals(applied.data())) {
                imported++;
            } else {
                refused.add(row.line());
            }
        }
        return Result.ok(new GlossaryImportReport(imported, malformed, refused, duplicates));
    }

    // A term named twice keeps the row that comes last, in the place of its first row.
    private static List<GlossaryCsv.Row> lastOfEachTerm(final List<GlossaryCsv.Row> rows) {
        final Map<String, GlossaryCsv.Row> byTerm = new LinkedHashMap<>();
        rows.forEach(row -> byTerm.put(GlossaryKeys.of(row.term()), row));
        return List.copyOf(byTerm.values());
    }

    private static Result<String> readUtf8(final String projectId, final Path source) {
        final byte[] bytes;
        try {
            bytes = Files.readAllBytes(source);
        } catch (IOException cause) {
            log.warn("Glossary import project={} path={} failed: file unreadable", projectId, source, cause);
            return Result.err(AppError.of(
                    ErrorCode.validation,
                    "Glossary file unreadable",
                    "The glossary file could not be read.",
                    null,
                    cause));
        }
        try {
            return Result.ok(StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString());
        } catch (CharacterCodingException notUtf8) {
            log.warn("Glossary import project={} path={} refused: the file is not UTF-8", projectId, source);
            return Result.err(fileError(
                    FailureReason.NOT_UTF8,
                    "The glossary file is not UTF-8 text",
                    "Save the file as CSV in UTF-8 and import it again."));
        }
    }

    private static AppError fileError(final FailureReason reason, final String title, final String message) {
        return AppError.of(
                ErrorCode.validation,
                title,
                message,
                SafeDetails.empty().withReason(reason).render(),
                null);
    }

    private static AppError exportError(final String projectId, final Path destination, final Throwable failure) {
        final AppError error = FileFailures.fixable(destination, failure)
                .orElseGet(() -> AppError.of(
                        ErrorCode.internal,
                        "Glossary not saved",
                        "The glossary file could not be written.",
                        null,
                        failure));
        if (error.code() == ErrorCode.internal) {
            log.error("Glossary export project={} path={} failed", projectId, destination, failure);
        } else {
            log.warn("Glossary export project={} path={} refused: {}", projectId, destination, error.details());
        }
        return error;
    }
}

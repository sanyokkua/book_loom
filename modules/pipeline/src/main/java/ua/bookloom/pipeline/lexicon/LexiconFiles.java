package ua.bookloom.pipeline.lexicon;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.LexiconRepository;
import ua.bookloom.api.pipeline.GlossaryImportReport;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;

/**
 * Reads and writes the recurring terms as a CSV file (15i.N7). An import adds the terms the lexicon lacks and gives a
 * term a rendering when it has none of the person's; a rendering the person typed is never replaced, one a reading
 * wrote is (the file is the person's word), and a term the glossary holds is refused, so a draft is never given two
 * renderings of one term.
 */
@Slf4j
@RequiredArgsConstructor
final class LexiconFiles {

    private enum Outcome {
        CHANGED,
        UNCHANGED,
        REFUSED
    }

    private final LexiconRepository lexicon;
    private final GlossaryRepository glossary;

    Result<GlossaryImportReport> importFrom(final String projectId, final Path source) {
        final String text;
        try {
            text = Files.readString(source, StandardCharsets.UTF_8);
        } catch (IOException cause) {
            log.warn("Lexicon import project={} path={} failed: file unreadable", projectId, source, cause);
            return Result.err(AppError.of(
                    ErrorCode.validation,
                    "Terms file unreadable",
                    "The recurring terms file could not be read.",
                    null,
                    cause));
        }
        log.debug("Lexicon import project={} path={} chars={}", projectId, source, text.length());
        return applyRows(projectId, LexiconCsv.parse(text)).map(report -> {
            log.info(
                    "Lexicon imported project={} path={} imported={} malformed={} refused={}",
                    projectId,
                    source,
                    report.imported(),
                    report.malformedLines().size(),
                    report.refusedLines().size());
            return report;
        });
    }

    Result<Path> exportTo(final String projectId, final Path destination) {
        final Result<List<LexiconEntry>> held = lexicon.all(projectId);
        if (held.isErr()) {
            return Result.err(Objects.requireNonNull(held.error(), "error"));
        }
        final List<LexiconEntry> entries = Objects.requireNonNull(held.data(), "held");
        try {
            Files.writeString(destination, LexiconCsv.format(entries), StandardCharsets.UTF_8);
        } catch (IOException cause) {
            final AppError error = AppError.of(
                    ErrorCode.internal,
                    "Terms not saved",
                    "The recurring terms file could not be written.",
                    null,
                    cause);
            log.error("Lexicon export project={} path={} failed code={}", projectId, destination, error.code(), cause);
            return Result.err(error);
        }
        log.info("Lexicon exported project={} path={} entries={}", projectId, destination, entries.size());
        return Result.ok(destination);
    }

    private Result<GlossaryImportReport> applyRows(final String projectId, final LexiconCsv.Parsed parsed) {
        final Result<List<LexiconEntry>> all = lexicon.all(projectId);
        if (all.isErr()) {
            return Result.err(Objects.requireNonNull(all.error(), "error"));
        }
        final Map<String, LexiconEntry> held = new LinkedHashMap<>();
        Objects.requireNonNull(all.data(), "held").forEach(entry -> held.put(LexiconEntry.keyOf(entry.term()), entry));
        final List<Integer> refused = new ArrayList<>();
        int imported = 0;
        for (final LexiconCsv.Row row : parsed.rows()) {
            final Result<Outcome> applied = applyRow(projectId, row, held.get(LexiconEntry.keyOf(row.term())));
            if (applied.isErr()) {
                return Result.err(Objects.requireNonNull(applied.error(), "error"));
            }
            switch (Objects.requireNonNull(applied.data(), "outcome")) {
                case CHANGED -> imported++;
                case REFUSED -> refused.add(row.line());
                case UNCHANGED -> log.debug("Lexicon import line {} changes nothing", row.line());
            }
        }
        return Result.ok(new GlossaryImportReport(imported, parsed.malformedLines(), refused, 0, parsed.problems()));
    }

    private Result<Outcome> applyRow(
            final String projectId, final LexiconCsv.Row row, @Nullable final LexiconEntry held) {
        final Result<Optional<GlossaryEntry>> inGlossary = glossary.findByTerm(projectId, row.term());
        if (inGlossary.isErr()) {
            return Result.err(Objects.requireNonNull(inGlossary.error(), "error"));
        }
        if (Objects.requireNonNull(inGlossary.data(), "glossary").isPresent()) {
            log.debug("Lexicon import refused line {}: the glossary holds the term", row.line());
            return Result.ok(Outcome.REFUSED);
        }
        final String target = row.target();
        if (held == null) {
            final LexiconEntry added = LexiconEntry.of(projectId, row.term());
            return lexicon.put(target == null ? added : added.withChosen(target))
                    .map(entry -> Outcome.CHANGED);
        }
        if (target == null || held.hasPersonsRendering()) {
            log.debug("Lexicon import line {}: no target or the person's rendering stands", row.line());
            return Result.ok(Outcome.UNCHANGED);
        }
        return lexicon.update(projectId, held.term(), found -> found.withChosen(target))
                .map(stored -> stored.isPresent() ? Outcome.CHANGED : Outcome.UNCHANGED);
    }
}

package ua.bookloom.pipeline.glossary;

import com.google.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.persistence.DeferralRepository;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.pipeline.EntryChanges;
import ua.bookloom.api.pipeline.GlossaryImportReport;
import ua.bookloom.api.pipeline.GlossaryReviewReport;
import ua.bookloom.api.pipeline.GlossaryService;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.UnknownGender;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.Project;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.Tokens;
import ua.bookloom.pipeline.project.OpenProjects;
import ua.bookloom.pipeline.revision.DeferralRegister;

/**
 * The one port Names &amp; style edits the glossary through. Every way in — the table, the Add term dialog and a CSV
 * import — meets the same two refusals: a duplicate term, and a locked term with no target (which would make the name
 * vanish from the book, since a locked term is replaced by its target).
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class GlossaryServiceImpl implements GlossaryService {

    static final String LOCKED_NEEDS_TARGET = "A locked term needs a target.";

    private final GlossaryRepository glossary;
    private final SegmentRepository segments;
    private final DeferralRepository deferrals;
    private final ProjectRepository projects;
    private final OpenProjects openProjects;
    private final GlossaryModelScans modelScans;

    @Override
    public Result<List<GlossaryEntry>> entries(final String projectId) {
        Objects.requireNonNull(projectId, "projectId");
        return guarded("entries", projectId, () -> glossary.all(projectId));
    }

    @Override
    public Result<List<UnknownGender>> unknownGenders(final String projectId, final int minMentions) {
        Objects.requireNonNull(projectId, "projectId");
        return guarded(
                "unknownGenders",
                projectId,
                () -> CharacterMentions.read(glossary, openProjects.get(projectId), projectId, minMentions));
    }

    @Override
    public Result<EntryChanges<GlossaryEntry>> scan(final String projectId) {
        Objects.requireNonNull(projectId, "projectId");
        return guarded("scan", projectId, () -> runScan(projectId).map(EntryChanges::ofAdded));
    }

    @Override
    public Result<EntryChanges<GlossaryEntry>> prescan(
            final String projectId, final ChatModel model, final Consumer<JobEvent> progress) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(progress, "progress");
        return guarded("prescan", projectId, () -> modelScans.prescan(projectId, model, progress));
    }

    @Override
    public Result<GlossaryReviewReport> review(
            final String projectId, final ChatModel model, final Consumer<JobEvent> progress) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(progress, "progress");
        return guarded("review", projectId, () -> modelScans.review(projectId, model, progress));
    }

    @Override
    public Result<EntryChanges<GlossaryEntry>> translate(
            final String projectId, final ChatModel model, final Consumer<JobEvent> progress) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(progress, "progress");
        return guarded(
                "translate",
                projectId,
                () -> GlossaryTranslation.run(glossary, modelScans, projectId, model, progress));
    }

    @Override
    public Result<GlossaryEntry> add(final GlossaryEntry entry) {
        Objects.requireNonNull(entry, "entry");
        return guarded("add", entry.projectId(), () -> addEntry(entry));
    }

    @Override
    public Result<GlossaryEntry> update(final GlossaryEntry entry) {
        Objects.requireNonNull(entry, "entry");
        return guarded("update", entry.projectId(), () -> updateEntry(entry));
    }

    @Override
    public Result<Boolean> remove(final String projectId, final String entryId) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(entryId, "entryId");
        return guarded("remove", projectId, () -> {
            final Result<Boolean> removed = glossary.remove(projectId, entryId);
            log.debug("Glossary remove project={} entryId={} outcome={}", projectId, entryId, removed.data());
            return removed;
        });
    }

    @Override
    public Result<GlossaryImportReport> importCsv(final String projectId, final Path source) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(source, "source");
        return guarded("importCsv", projectId, () -> readAndImport(projectId, source));
    }

    @Override
    public Result<Path> exportCsv(final String projectId, final Path destination) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(destination, "destination");
        return guarded("exportCsv", projectId, () -> writeExport(projectId, destination));
    }

    private Result<List<GlossaryEntry>> runScan(final String projectId) {
        final Document document = openProjects.get(projectId);
        if (document == null) {
            return Result.err(notOpen(projectId));
        }
        return glossary.all(projectId).flatMap(held -> scanIfEmpty(projectId, document, held));
    }

    private Result<List<GlossaryEntry>> scanIfEmpty(
            final String projectId, final Document document, final List<GlossaryEntry> held) {
        if (!held.isEmpty()) {
            log.debug("Glossary scan project={} skipped: the glossary holds {} entries", projectId, held.size());
            return Result.ok(List.of());
        }
        log.info("Glossary scan started project={}", projectId);
        final String language = sourceLanguage(projectId, document);
        final List<Segment> body = bodySegments(document);
        final Result<List<GlossaryEntry>> added = FrequencyScan.newTerms(projectId, body, language, glossary)
                .map(proposals -> GivenNames.seeded(proposals, language))
                .map(proposals -> PronounGender.seeded(proposals, Tokens.visibleTexts(body), language))
                .flatMap(this::addAll);
        log.info(
                "Glossary scan finished project={} ok={} entriesAdded={}",
                projectId,
                added.isOk(),
                added.isOk() ? Objects.requireNonNull(added.data(), "added").size() : 0);
        return added;
    }

    private @Nullable String sourceLanguage(final String projectId, final Document document) {
        final String chosen = Optional.ofNullable(projects.find(projectId).data())
                .flatMap(project -> project.map(Project::brief))
                .map(BookBrief::sourceLanguage)
                .orElse(null);
        log.debug(
                "Glossary scan project={} language chosen={} declared={}", projectId, chosen, document.declaredLang());
        return chosen == null ? document.declaredLang() : chosen;
    }

    private Result<GlossaryEntry> addEntry(final GlossaryEntry entry) {
        if (isLockedWithoutTarget(entry)) {
            return refuseLockedWithoutTarget(entry);
        }
        final Result<Optional<GlossaryEntry>> held = glossary.findByTerm(entry.projectId(), entry.term());
        if (held.isErr()) {
            return Result.err(Objects.requireNonNull(held.error(), "error"));
        }
        if (Objects.requireNonNull(held.data(), "held").isPresent()) {
            log.warn("Glossary add refused: term already held project={}", entry.projectId());
            log.trace("Glossary add refused: duplicate term {}", entry.term());
            return Result.err(AppError.of(
                    ErrorCode.validation,
                    "Duplicate glossary term",
                    "'" + entry.term() + "' is already in the glossary."));
        }
        final Result<GlossaryEntry> added = glossary.add(entry);
        log.debug("Glossary add project={} entryId={} ok={}", entry.projectId(), entry.id(), added.isOk());
        return added;
    }

    private Result<GlossaryEntry> updateEntry(final GlossaryEntry entry) {
        if (isLockedWithoutTarget(entry)) {
            return refuseLockedWithoutTarget(entry);
        }
        final Result<List<GlossaryEntry>> all = glossary.all(entry.projectId());
        if (all.isErr()) {
            return Result.err(Objects.requireNonNull(all.error(), "error"));
        }
        final Optional<GlossaryEntry> before = Objects.requireNonNull(all.data(), "all").stream()
                .filter(held -> held.id().equals(entry.id()))
                .findFirst();
        if (before.isEmpty()) {
            log.debug("Glossary update project={} entryId={} refused: unknown entry", entry.projectId(), entry.id());
            return glossary.update(entry);
        }
        return glossary.update(entry).flatMap(stored -> recordTermChange(before.get(), stored));
    }

    private Result<GlossaryEntry> recordTermChange(final GlossaryEntry before, final GlossaryEntry after) {
        log.debug("Glossary update project={} entryId={} stored", after.projectId(), after.id());
        if (Objects.equals(before.target(), after.target())) {
            return Result.ok(after);
        }
        final Result<List<SegmentRecord>> decided = segments.all(after.projectId());
        if (decided.isErr()) {
            return Result.err(Objects.requireNonNull(decided.error(), "error"));
        }
        for (final Deferral deferral :
                DeferralRegister.termChanged(before, after, Objects.requireNonNull(decided.data(), "decided"))) {
            final Result<Deferral> added = deferrals.add(deferral);
            if (added.isErr()) {
                return Result.err(Objects.requireNonNull(added.error(), "error"));
            }
        }
        return Result.ok(after);
    }

    private Result<List<GlossaryEntry>> addAll(final List<GlossaryEntry> entries) {
        final List<GlossaryEntry> added = new ArrayList<>();
        for (final GlossaryEntry entry : entries) {
            final Result<GlossaryEntry> stored = glossary.add(entry);
            if (stored.isErr()) {
                return Result.err(Objects.requireNonNull(stored.error(), "error"));
            }
            added.add(entry);
        }
        log.debug("Glossary scan added {} entries", added.size());
        return Result.ok(added);
    }

    private Result<GlossaryImportReport> readAndImport(final String projectId, final Path source) {
        final String text;
        try {
            text = Files.readString(source, StandardCharsets.UTF_8);
        } catch (IOException cause) {
            log.warn("Glossary import project={} path={} failed: file unreadable", projectId, source, cause);
            return Result.err(AppError.of(
                    ErrorCode.validation,
                    "Glossary file unreadable",
                    "The glossary file could not be read.",
                    null,
                    cause));
        }
        log.debug("Glossary import project={} path={} chars={}", projectId, source, text.length());
        final GlossaryCsv.Parsed parsed = GlossaryCsv.parse(text);
        final Result<GlossaryImportReport> report = applyRows(projectId, parsed);
        if (report.isOk()) {
            logImport(projectId, source, Objects.requireNonNull(report.data(), "report"));
        }
        return report;
    }

    private Result<GlossaryImportReport> applyRows(final String projectId, final GlossaryCsv.Parsed parsed) {
        final List<Integer> refused = new ArrayList<>();
        int imported = 0;
        for (final GlossaryCsv.Row row : parsed.rows()) {
            final Result<Boolean> applied = applyRow(projectId, row);
            if (applied.isErr()) {
                return Result.err(Objects.requireNonNull(applied.error(), "error"));
            }
            if (Boolean.TRUE.equals(applied.data())) {
                imported++;
            } else {
                refused.add(row.line());
            }
        }
        return Result.ok(new GlossaryImportReport(imported, parsed.malformedLines(), refused));
    }

    private static void logImport(final String projectId, final Path source, final GlossaryImportReport report) {
        report.malformedLines().forEach(line -> log.warn("Glossary import path={} line {} is malformed", source, line));
        report.refusedLines()
                .forEach(line ->
                        log.warn("Glossary import path={} line {} refused: {}", source, line, LOCKED_NEEDS_TARGET));
        log.info(
                "Glossary imported project={} path={} imported={} malformed={} refused={}",
                projectId,
                source,
                report.imported(),
                report.malformedLines().size(),
                report.refusedLines().size());
    }

    private Result<Boolean> applyRow(final String projectId, final GlossaryCsv.Row row) {
        if (row.locked() && isBlank(row.target())) {
            return Result.ok(Boolean.FALSE);
        }
        final Result<Optional<GlossaryEntry>> held = glossary.findByTerm(projectId, row.term());
        if (held.isErr()) {
            return Result.err(Objects.requireNonNull(held.error(), "error"));
        }
        final Optional<GlossaryEntry> existing = Objects.requireNonNull(held.data(), "held");
        final Result<GlossaryEntry> stored = existing.isPresent()
                ? updateEntry(GlossaryCsv.replaced(existing.get(), row))
                : addEntry(new GlossaryEntry(
                        GlossaryIds.of(projectId, row.term()),
                        projectId,
                        row.term(),
                        row.target(),
                        row.type(),
                        row.gender(),
                        row.locked()));
        return stored.map(entry -> Boolean.TRUE);
    }

    private Result<Path> writeExport(final String projectId, final Path destination) {
        final Result<List<GlossaryEntry>> held = glossary.all(projectId);
        if (held.isErr()) {
            return Result.err(Objects.requireNonNull(held.error(), "error"));
        }
        final List<GlossaryEntry> entries = Objects.requireNonNull(held.data(), "held");
        try {
            Files.writeString(destination, GlossaryCsv.format(entries), StandardCharsets.UTF_8);
        } catch (IOException cause) {
            final AppError error = AppError.of(
                    ErrorCode.internal, "Glossary not saved", "The glossary file could not be written.", null, cause);
            log.error("Glossary export project={} path={} failed code={}", projectId, destination, error.code(), cause);
            return Result.err(error);
        }
        log.info("Glossary exported project={} path={} entries={}", projectId, destination, entries.size());
        return Result.ok(destination);
    }

    static List<Segment> bodySegments(final Document document) {
        return FrequencyScan.storyText(document);
    }

    private static boolean isLockedWithoutTarget(final GlossaryEntry entry) {
        return entry.locked() && isBlank(entry.target());
    }

    private static boolean isBlank(@Nullable final String target) {
        return target == null || target.isBlank();
    }

    private static Result<GlossaryEntry> refuseLockedWithoutTarget(final GlossaryEntry entry) {
        log.warn(
                "Glossary change refused project={} entryId={}: {}",
                entry.projectId(),
                entry.id(),
                LOCKED_NEEDS_TARGET);
        return Result.err(AppError.of(ErrorCode.validation, "Locked term has no target", LOCKED_NEEDS_TARGET));
    }

    private static AppError notOpen(final String projectId) {
        log.warn("Glossary scan project={} refused: no book is open", projectId);
        return AppError.of(ErrorCode.validation, "No book is open", "Open the book before scanning for names.");
    }

    private static <T> Result<T> guarded(final String action, final String projectId, final Supplier<Result<T>> body) {
        log.debug("Glossary {} project={}", action, projectId);
        try {
            return body.get();
        } catch (Throwable cause) {
            final AppError error = AppError.of(
                    ErrorCode.internal, "Glossary failed", "The glossary could not be updated.", null, cause);
            log.error(
                    "Unexpected glossary failure action={} project={} code={}", action, projectId, error.code(), cause);
            return Result.err(error);
        }
    }
}

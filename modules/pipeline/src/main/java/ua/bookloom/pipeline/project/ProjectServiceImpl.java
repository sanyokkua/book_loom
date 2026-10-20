package ua.bookloom.pipeline.project;

import com.google.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookInspection;
import ua.bookloom.api.document.BookInspector;
import ua.bookloom.api.document.BookProfile;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.InspectionVerdict;
import ua.bookloom.api.document.LanguageEvidence;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.pipeline.BookPlan;
import ua.bookloom.api.pipeline.ImportedBook;
import ua.bookloom.api.pipeline.ProjectService;
import ua.bookloom.api.pipeline.RoundTripReport;
import ua.bookloom.api.pipeline.SegmentPreview;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.NarratorHint;
import ua.bookloom.api.project.Project;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.SourceLanguage;
import ua.bookloom.pipeline.chunk.Chunk;
import ua.bookloom.pipeline.index.BookIndex;
import ua.bookloom.pipeline.narrator.NarratorDetector;
import ua.bookloom.util.hash.HashUtil;
import ua.bookloom.util.lang.LanguageTags;

/**
 * Imports a book once, keeps it open in {@link OpenProjects} and answers every later question from the stored
 * project id. A refusal by inspection is data, not an error (ADR-0039).
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class ProjectServiceImpl implements ProjectService {

    private static final int PROJECT_ID_LENGTH = 12;

    private final BookInspector inspector;
    private final DocumentPort documents;
    private final ProjectRepository projects;
    private final SegmentRepository segments;
    private final OpenProjects openProjects;
    private final Clock clock;

    @Override
    public Result<ImportedBook> importBook(final Path source) {
        Objects.requireNonNull(source, "source");
        try {
            log.debug("importBook path={}", source);
            final Result<BookInspection> inspected = inspector.inspect(source);
            if (inspected.isErr()) {
                return Result.err(errorOf(inspected));
            }
            final BookInspection inspection = Objects.requireNonNull(inspected.data(), "inspection");
            if (inspection.verdict() != InspectionVerdict.READABLE) {
                log.warn(
                        "import refused path={} verdict={} detectedType={}",
                        source,
                        inspection.verdict(),
                        inspection.detectedType());
                return Result.ok(new ImportedBook(null, inspection, null, null));
            }
            return openAndStore(source, inspection);
        } catch (Throwable cause) {
            return Result.err(internalError("import the book", cause));
        }
    }

    @Override
    public Result<Integer> decidedSegments(final Path source) {
        Objects.requireNonNull(source, "source");
        try {
            final String id = projectId(source.toAbsolutePath().normalize());
            final Result<Optional<Project>> found = projects.find(id);
            if (found.isErr()) {
                return Result.err(errorOf(found));
            }
            final Project stored = Objects.requireNonNull(found.data(), "found").orElse(null);
            if (stored == null || !StoredBooks.isUnchanged(stored, source, documents)) {
                log.debug("decidedSegments project={}: nothing stored for this content", id);
                return Result.ok(0);
            }
            final Result<Integer> counted = segments.all(id).map(StoredBooks::countDecided);
            log.debug("decidedSegments project={} decided={}", id, counted.data());
            return counted;
        } catch (Throwable cause) {
            return Result.err(internalError("count the decided segments", cause));
        }
    }

    @Override
    public Result<ImportedBook> continueBook(final Path source) {
        Objects.requireNonNull(source, "source");
        try {
            final Path normalized = source.toAbsolutePath().normalize();
            final String id = projectId(normalized);
            log.debug("continueBook path={} project={}", source, id);
            final Result<Optional<Project>> found = projects.find(id);
            if (found.isErr()) {
                return Result.err(errorOf(found));
            }
            final Project stored = Objects.requireNonNull(found.data(), "found").orElse(null);
            if (stored == null || !StoredBooks.isUnchanged(stored, source, documents)) {
                log.warn("continueBook refused project={}: nothing stored for this content", id);
                return Result.err(StoredBooks.nothingToContinue());
            }
            return inspector.inspect(source).flatMap(inspection -> reopen(stored, source, inspection));
        } catch (Throwable cause) {
            return Result.err(internalError("continue the book", cause));
        }
    }

    private Result<ImportedBook> reopen(final Project stored, final Path source, final BookInspection inspection) {
        if (inspection.verdict() != InspectionVerdict.READABLE) {
            return Result.ok(new ImportedBook(null, inspection, null, null));
        }
        final Document previous = openProjects.remove(stored.id());
        if (previous != null) {
            log.debug("continue replaces the open book of project={}", stored.id());
            documents.close(previous);
        }
        return documents
                .open(source)
                .flatMap(document ->
                        closingUnlessImported(document, () -> profileAndKeep(stored, inspection, document)));
    }

    private Result<ImportedBook> profileAndKeep(
            final Project stored, final BookInspection inspection, final Document document) {
        return inspector
                .profile(document)
                .flatMap(profile -> segments.all(stored.id())
                        .map(records -> imported(
                                stored.id(),
                                stored.source(),
                                inspection,
                                profile,
                                stored.brief(),
                                document,
                                records.size())));
    }

    @Override
    public Result<Project> updateBrief(final String projectId, final BookBrief brief) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(brief, "brief");
        try {
            log.debug(
                    "updateBrief project={} dial={} source={} target={}",
                    projectId,
                    brief.dial(),
                    brief.sourceLanguage(),
                    brief.targetLanguage());
            return findProject(projectId).flatMap(project -> projects.save(project.withBrief(brief)));
        } catch (Throwable cause) {
            return Result.err(internalError("update the brief", cause));
        }
    }

    @Override
    public Result<BookPlan> plan(final String projectId) {
        Objects.requireNonNull(projectId, "projectId");
        try {
            final Document document = openProjects.get(projectId);
            if (document == null) {
                return Result.err(unknownProject(projectId));
            }
            return findProject(projectId).map(project -> plan(projectId, document, project.brief()));
        } catch (Throwable cause) {
            return Result.err(internalError("plan the book", cause));
        }
    }

    @Override
    public Result<List<SegmentPreview>> segments(final String projectId, final String unitId) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(unitId, "unitId");
        try {
            log.debug("segments project={} unit={}", projectId, unitId);
            final Document document = openProjects.get(projectId);
            if (document == null) {
                return Result.err(unknownProject(projectId));
            }
            final Unit unit = document.units().stream()
                    .filter(candidate -> candidate.id().equals(unitId))
                    .findFirst()
                    .orElse(null);
            if (unit == null) {
                return Result.err(unknownUnit(unitId));
            }
            return findProject(projectId)
                    .map(project -> UnitChunks.previews(unit, project.brief(), SegmentLocators.of(document)));
        } catch (Throwable cause) {
            return Result.err(internalError("list the segments", cause));
        }
    }

    @Override
    public Result<RoundTripReport> roundTrip(final String projectId) {
        Objects.requireNonNull(projectId, "projectId");
        try {
            return findProject(projectId)
                    .flatMap(project -> new RoundTripCheck(inspector, documents).run(projectId, project.source()));
        } catch (Throwable cause) {
            return Result.err(internalError("run the round-trip check", cause));
        }
    }

    @Override
    public Result<Boolean> close(final String projectId) {
        Objects.requireNonNull(projectId, "projectId");
        try {
            log.debug("close project={}", projectId);
            final Document document = openProjects.remove(projectId);
            if (document == null) {
                return Result.err(unknownProject(projectId));
            }
            return documents.close(document);
        } catch (Throwable cause) {
            return Result.err(internalError("close the project", cause));
        }
    }

    private Result<ImportedBook> openAndStore(final Path source, final BookInspection inspection) {
        final Path normalized = source.toAbsolutePath().normalize();
        final String id = projectId(normalized);
        final Document previous = openProjects.remove(id);
        if (previous != null) {
            log.debug("import replaces the open book of project={}", id);
            documents.close(previous);
        }
        return documents
                .open(source)
                .flatMap(document ->
                        closingUnlessImported(document, () -> profileAndStore(id, normalized, inspection, document)));
    }

    // The opened document is closed whenever the import does not complete, a failure that is thrown included.
    private Result<ImportedBook> closingUnlessImported(
            final Document document, final Supplier<Result<ImportedBook>> finish) {
        boolean imported = false;
        try {
            final Result<ImportedBook> stored = finish.get();
            imported = stored.isOk();
            return stored;
        } finally {
            if (!imported) {
                documents.close(document);
            }
        }
    }

    private Result<ImportedBook> profileAndStore(
            final String id, final Path source, final BookInspection inspection, final Document document) {
        return inspector.profile(document).flatMap(profile -> {
            final BookBrief brief = BookBrief.defaults(preselectSource(inspection.languageEvidence()));
            final Project project = StoredBooks.newProject(id, source, document, brief, clock.instant());
            final List<SegmentRecord> records = StoredBooks.pendingRecords(id, document);
            return projects.save(project)
                    .flatMap(saved -> segments.saveAll(id, records))
                    .map(count -> imported(id, source, inspection, profile, brief, document, count));
        });
    }

    private ImportedBook imported(
            final String id,
            final Path source,
            final BookInspection inspection,
            final BookProfile profile,
            final BookBrief brief,
            final Document document,
            final int segmentCount) {
        final BookIndex index = buildIndex(id, document);
        final NarratorHint hint = NarratorDetector.detect(document, SourceLanguage.forAnalysis(brief, document))
                .orElse(null);
        openProjects.put(id, document, index);
        log.info(
                "book imported path={} format={} units={} segments={} project={}",
                source,
                document.format(),
                document.units().size(),
                segmentCount,
                id);
        return new ImportedBook(id, inspection, profile, brief, hint);
    }

    // Built, like the narrator hint, before the book is registered, so a failure leaves nothing open.
    private static BookIndex buildIndex(final String projectId, final Document document) {
        final BookIndex index = BookIndex.of(document);
        log.info(
                "book index built project={} segments={} words={} millis={}",
                projectId,
                index.size(),
                index.distinctWords(),
                index.buildMillis());
        return index;
    }

    private static @Nullable String preselectSource(final LanguageEvidence evidence) {
        final String chosen =
                switch (evidence.verdict()) {
                    case MISMATCH -> evidence.contentMajority();
                    case MATCH -> LanguageTags.normalize(evidence.declared()).orElse(null);
                    case ABSENT -> evidence.contentMajority();
                    case UNRECOGNIZED -> null;
                };
        log.debug(
                "source preselection verdict={} declared={} contentMajority={} chosen={}",
                evidence.verdict(),
                evidence.declared(),
                evidence.contentMajority(),
                chosen);
        return chosen;
    }

    private static BookPlan plan(final String projectId, final Document document, final BookBrief brief) {
        final Map<String, Integer> perUnit = new LinkedHashMap<>();
        final List<String> oversized = new ArrayList<>();
        for (final Unit unit : document.units()) {
            final List<Chunk> chunks = UnitChunks.pack(unit, brief);
            perUnit.put(unit.id(), chunks.size());
            chunks.stream()
                    .filter(Chunk::oversized)
                    .forEach(chunk -> oversized.add(chunk.segments().get(0).id()));
        }
        log.debug(
                "plan project={} chunks={} oversized={} keptKinds={}",
                projectId,
                perUnit.values().stream().mapToInt(Integer::intValue).sum(),
                oversized.size(),
                brief.alsoTranslate().keptKinds());
        return new BookPlan(perUnit, oversized);
    }

    private Result<Project> findProject(final String projectId) {
        return projects.find(projectId)
                .flatMap(found -> found.map(Result::ok).orElseGet(() -> Result.err(unknownProject(projectId))));
    }

    private static String projectId(final Path normalizedPath) {
        final String hash = HashUtil.sha256Hex(normalizedPath.toString().getBytes(StandardCharsets.UTF_8));
        return hash.substring(0, PROJECT_ID_LENGTH);
    }

    private static AppError errorOf(final Result<?> result) {
        return Objects.requireNonNull(result.error(), "error");
    }

    private static AppError unknownProject(final String projectId) {
        log.warn("unknown project={}", projectId);
        return AppError.of(
                ErrorCode.validation,
                "This project is not open",
                "No open project has this id; import the book again.");
    }

    private static AppError unknownUnit(final String unitId) {
        log.warn("unknown unit={}", unitId);
        return AppError.of(
                ErrorCode.validation, "This part of the book is not known", "The book has no part with this id.");
    }

    private static AppError internalError(final String action, final Throwable cause) {
        log.error("Failed to {}", action, cause);
        return AppError.of(
                ErrorCode.internal, "The project service failed", "The book could not be processed.", null, cause);
    }
}

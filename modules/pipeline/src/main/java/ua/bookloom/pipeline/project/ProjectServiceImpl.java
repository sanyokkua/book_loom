package ua.bookloom.pipeline.project;

import com.google.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.pipeline.BookPlan;
import ua.bookloom.api.pipeline.ImportedBook;
import ua.bookloom.api.pipeline.ProjectService;
import ua.bookloom.api.pipeline.RoundTripReport;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Project;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.chunk.Chunk;
import ua.bookloom.pipeline.chunk.ChunkPacker;
import ua.bookloom.pipeline.chunk.TokenBudget;
import ua.bookloom.pipeline.dial.DialParameters;
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
    public Result<RoundTripReport> roundTrip(final String projectId) {
        Objects.requireNonNull(projectId, "projectId");
        return Result.err(AppError.of(
                ErrorCode.internal, "Round-trip check unavailable", "The round-trip check is not available yet."));
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
        return documents.open(source).flatMap(document -> {
            final Result<ImportedBook> stored = profileAndStore(id, normalized, inspection, document);
            if (stored.isErr()) {
                documents.close(document);
            }
            return stored;
        });
    }

    private Result<ImportedBook> profileAndStore(
            final String id, final Path source, final BookInspection inspection, final Document document) {
        return inspector.profile(document).flatMap(profile -> {
            final BookBrief brief = BookBrief.defaults(preselectSource(inspection.languageEvidence()));
            final Project project = new Project(id, source, document.format(), document.contentHash(), brief);
            final List<SegmentRecord> records = pendingRecords(id, document);
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
        openProjects.put(id, document);
        log.info(
                "book imported path={} format={} units={} segments={} project={}",
                source,
                document.format(),
                document.units().size(),
                segmentCount,
                id);
        return new ImportedBook(id, inspection, profile, brief);
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

    private static List<SegmentRecord> pendingRecords(final String projectId, final Document document) {
        final List<SegmentRecord> records = new ArrayList<>();
        for (final Unit unit : document.units()) {
            for (final Segment segment : unit.segments()) {
                records.add(new SegmentRecord(
                        projectId,
                        segment.id(),
                        unit.id(),
                        segment.order(),
                        segment.kind(),
                        SegmentStatus.PENDING,
                        null,
                        null,
                        null,
                        null,
                        0.0,
                        null,
                        List.of(),
                        SegmentPath.DRAFT,
                        0,
                        false,
                        null));
            }
        }
        return records;
    }

    private static BookPlan plan(final String projectId, final Document document, final BookBrief brief) {
        final Set<SegmentKind> kept = brief.alsoTranslate().keptKinds();
        final int cap = DialParameters.of(brief.dial()).chunkCap();
        final Map<String, Integer> perUnit = new LinkedHashMap<>();
        final List<String> oversized = new ArrayList<>();
        for (final Unit unit : document.units()) {
            final List<Segment> translated = unit.segments().stream()
                    .filter(segment -> !(unit.isAuxiliary() && kept.contains(segment.kind())))
                    .toList();
            final List<Chunk> chunks =
                    ChunkPacker.pack(translated, brief.sourceLanguage(), TokenBudget.MAX_CHUNK_TOKENS, cap);
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
                kept);
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

    private static AppError internalError(final String action, final Throwable cause) {
        log.error("Failed to {}", action, cause);
        return AppError.of(
                ErrorCode.internal, "The project service failed", "The book could not be processed.", null, cause);
    }
}

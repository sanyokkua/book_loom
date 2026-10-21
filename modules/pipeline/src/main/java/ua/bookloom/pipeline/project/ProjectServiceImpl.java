package ua.bookloom.pipeline.project;

import com.google.inject.Inject;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
import ua.bookloom.api.persistence.RunRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.pipeline.BookPlan;
import ua.bookloom.api.pipeline.BookRelocation;
import ua.bookloom.api.pipeline.ImportedBook;
import ua.bookloom.api.pipeline.ProjectChange;
import ua.bookloom.api.pipeline.ProjectOpening;
import ua.bookloom.api.pipeline.ProjectService;
import ua.bookloom.api.pipeline.ProjectSummary;
import ua.bookloom.api.pipeline.RoundTripReport;
import ua.bookloom.api.pipeline.SegmentPreview;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.IdSource;
import ua.bookloom.api.project.NarratorHint;
import ua.bookloom.api.project.Project;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.SourceLanguage;
import ua.bookloom.pipeline.index.BookIndex;
import ua.bookloom.pipeline.narrator.NarratorDetector;
import ua.bookloom.util.lang.LanguageTags;

/**
 * Imports a book once, keeps it open in {@link OpenProjects} and answers every later question from the stored
 * project id. A refusal by inspection is data, not an error (ADR-0039).
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class ProjectServiceImpl implements ProjectService {

    private final BookInspector inspector;
    private final DocumentPort documents;
    private final ProjectRepository projects;
    private final SegmentRepository segments;
    private final OpenProjects openProjects;
    private final Clock clock;
    private final IdSource ids;
    private final RunRepository runs;
    private final ProjectCopies copies;

    @Override
    public Result<ImportedBook> create(final Path source) {
        Objects.requireNonNull(source, "source");
        try {
            log.debug("create path={}", source);
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
    public Result<List<ProjectSummary>> list() {
        try {
            return new ProjectSummaries(documents, projects, segments).list();
        } catch (Throwable cause) {
            return Result.err(internalError("list the projects", cause));
        }
    }

    @Override
    public Result<ProjectOpening> open(final String projectId) {
        Objects.requireNonNull(projectId, "projectId");
        try {
            log.debug("open project={}", projectId);
            return findProject(projectId)
                    .flatMap(stored -> opener().open(
                                    stored,
                                    (project, inspection, profile, document, count) -> imported(
                                            project.id(),
                                            project.source(),
                                            inspection,
                                            profile,
                                            project.brief(),
                                            document,
                                            count)));
        } catch (Throwable cause) {
            return Result.err(internalError("open the project", cause));
        }
    }

    private ProjectOpener opener() {
        return new ProjectOpener(inspector, documents, projects, segments, runs, openProjects, clock);
    }

    @Override
    public Result<ProjectChange> updateBrief(final String projectId, final BookBrief brief) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(brief, "brief");
        try {
            log.debug(
                    "updateBrief project={} dial={} source={} target={}",
                    projectId,
                    brief.dial(),
                    brief.sourceLanguage(),
                    brief.targetLanguage());
            return findProject(projectId).flatMap(project -> new ProjectRenames(projects).updateBrief(project, brief));
        } catch (Throwable cause) {
            return Result.err(internalError("update the brief", cause));
        }
    }

    @Override
    public Result<ProjectChange> rename(final String projectId, final @Nullable String name) {
        Objects.requireNonNull(projectId, "projectId");
        try {
            log.debug("rename project={} automatic={}", projectId, name == null);
            return findProject(projectId).flatMap(project -> new ProjectRenames(projects).rename(project, name));
        } catch (Throwable cause) {
            return Result.err(internalError("rename the project", cause));
        }
    }

    @Override
    public Result<Boolean> remove(final String projectId) {
        Objects.requireNonNull(projectId, "projectId");
        try {
            log.debug("remove project={}", projectId);
            return findProject(projectId).flatMap(project -> {
                final Document open = openProjects.remove(projectId);
                if (open != null) {
                    documents.close(open);
                }
                log.info("project removed project={}", projectId);
                return projects.delete(projectId);
            });
        } catch (Throwable cause) {
            return Result.err(internalError("remove the project", cause));
        }
    }

    @Override
    public Result<ProjectChange> translateInto(
            final String projectId, final String target, final @Nullable String name) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(target, "target");
        try {
            log.debug("translateInto project={} target={} typedName={}", projectId, target, name != null);
            return findProject(projectId).flatMap(source -> {
                final Project candidate =
                        ProjectNames.translatedCopy(source, ids.newId(), target, name, clock.instant());
                return new ProjectTranslations(projects, copies).copyUnlessClash(source, candidate);
            });
        } catch (Throwable cause) {
            return Result.err(internalError("translate the book into another language", cause));
        }
    }

    @Override
    public Result<BookRelocation> relocate(final String projectId, final Path chosen) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(chosen, "chosen");
        try {
            log.debug("relocate project={}", projectId);
            return findProject(projectId).flatMap(project -> opener().relocate(project, chosen));
        } catch (Throwable cause) {
            return Result.err(internalError("find the book file", cause));
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
            return findProject(projectId).map(project -> UnitChunks.plan(projectId, document, project.brief()));
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
        final BookBrief brief = BookBrief.defaults(preselectSource(inspection.languageEvidence()));
        return documents
                .open(source)
                .flatMap(document -> ProjectOpener.closingUnlessImported(
                        documents, document, () -> storeUnderId(normalized, inspection, document, brief)));
    }

    private Result<ImportedBook> storeUnderId(
            final Path source, final BookInspection inspection, final Document document, final BookBrief brief) {
        final String id =
                reusableProject(document.contentHash(), brief).map(Project::id).orElseGet(ids::newId);
        log.debug("create uses project={}", id);
        final Document previous = openProjects.remove(id);
        if (previous != null) {
            log.debug("import replaces the open book of project={}", id);
            documents.close(previous);
        }
        return profileAndStore(id, source, inspection, document, brief);
    }

    private Result<ImportedBook> profileAndStore(
            final String id,
            final Path source,
            final BookInspection inspection,
            final Document document,
            final BookBrief brief) {
        return inspector.profile(document).flatMap(profile -> {
            final String title = ProjectNames.titleOf(profile.title(), source);
            final Project project = StoredBooks.newProject(id, source, document, brief, title, clock.instant());
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

    private Result<Project> findProject(final String projectId) {
        return projects.find(projectId)
                .flatMap(found -> found.map(Result::ok).orElseGet(() -> Result.err(unknownProject(projectId))));
    }

    // A project with no target cannot hold a translation, so importing the book again reuses it.
    private Optional<Project> reusableProject(final String contentHash, final BookBrief brief) {
        final List<Project> stored = projects.all().data();
        final Optional<Project> reusable =
                stored == null ? Optional.empty() : ProjectNames.reusable(contentHash, brief.sourceLanguage(), stored);
        log.debug("create reuse={} for the book", reusable.isPresent());
        return reusable;
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

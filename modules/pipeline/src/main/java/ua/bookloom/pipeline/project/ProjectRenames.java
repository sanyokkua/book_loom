package ua.bookloom.pipeline.project;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.pipeline.ProjectChange;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.NameOrigin;
import ua.bookloom.api.project.Project;

/** Stores a changed brief or name after the book's clash rules: a clash is answered as data and stores nothing. */
@Slf4j
@RequiredArgsConstructor
final class ProjectRenames {

    private final ProjectRepository projects;

    Result<ProjectChange> updateBrief(final Project project, final BookBrief brief) {
        final boolean pairChanged = !Objects.equals(project.brief().sourceLanguage(), brief.sourceLanguage())
                || !Objects.equals(project.brief().targetLanguage(), brief.targetLanguage());
        Project candidate = project.withBrief(brief);
        if (pairChanged && project.nameOrigin() == NameOrigin.AUTO) {
            candidate = candidate.withName(automaticName(candidate), NameOrigin.AUTO);
        }
        final boolean nameChanged = !candidate.name().equals(project.name());
        log.debug("updateBrief project={} pairChanged={} nameChanged={}", project.id(), pairChanged, nameChanged);
        if (!pairChanged && !nameChanged) {
            return projects.save(candidate).map(ProjectChange.Saved::new);
        }
        return saveUnlessClash(candidate);
    }

    Result<ProjectChange> rename(final Project project, final @Nullable String name) {
        if (name != null && name.isBlank()) {
            log.warn("rename refused project={}: blank name", project.id());
            return Result.err(AppError.of(
                    ErrorCode.validation,
                    "The name is empty",
                    "A project needs a name; type one or use the automatic."));
        }
        final Project candidate = name == null
                ? project.withName(automaticName(project), NameOrigin.AUTO)
                : project.withName(name.strip(), NameOrigin.USER);
        final Result<ProjectChange> result = saveUnlessClash(candidate);
        if (result.data() instanceof ProjectChange.Saved) {
            log.info("project renamed project={} origin={}", project.id(), candidate.nameOrigin());
        }
        return result;
    }

    private Result<ProjectChange> saveUnlessClash(final Project candidate) {
        return projects.all().flatMap(stored -> clashOrSave(candidate, stored));
    }

    private Result<ProjectChange> clashOrSave(final Project candidate, final List<Project> stored) {
        final Optional<ProjectChange> clash = ProjectNames.conflictOf(candidate, stored);
        if (clash.isPresent()) {
            log.warn(
                    "change not saved project={} clash={}",
                    candidate.id(),
                    clash.get().getClass().getSimpleName());
            return Result.ok(clash.get());
        }
        return projects.save(candidate).map(ProjectChange.Saved::new);
    }

    private static String automaticName(final Project project) {
        return ProjectNames.automatic(
                project.title(),
                project.brief().sourceLanguage(),
                project.brief().targetLanguage());
    }
}

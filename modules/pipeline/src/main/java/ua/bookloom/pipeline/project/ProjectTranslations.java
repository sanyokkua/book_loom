package ua.bookloom.pipeline.project;

import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.pipeline.ProjectChange;
import ua.bookloom.api.project.Project;

/** Stores a project of the same book for another target, or answers the clash as data; a part-way failure undoes it. */
@Slf4j
@RequiredArgsConstructor
final class ProjectTranslations {

    private final ProjectRepository projects;
    private final ProjectCopies copies;

    Result<ProjectChange> copyUnlessClash(final Project source, final Project candidate) {
        return projects.all().flatMap(stored -> {
            final Optional<ProjectChange> clash = ProjectNames.conflictOf(candidate, stored);
            if (clash.isPresent()) {
                log.warn(
                        "translateInto refused project={} clash={}",
                        source.id(),
                        clash.get().getClass().getSimpleName());
                return Result.ok(clash.get());
            }
            return saveAndCopy(source, candidate);
        });
    }

    private Result<ProjectChange> saveAndCopy(final Project source, final Project candidate) {
        final Result<Project> saved = projects.save(candidate);
        if (saved.isErr()) {
            return Result.err(Objects.requireNonNull(saved.error(), "error"));
        }
        final Result<Integer> copied = copies.copy(source.id(), candidate.id());
        if (copied.isErr()) {
            log.warn("translateInto failed project={} new={}: rolling back", source.id(), candidate.id());
            projects.delete(candidate.id());
            return Result.err(Objects.requireNonNull(copied.error(), "error"));
        }
        log.info(
                "project translated into project={} new={} target={}",
                source.id(),
                candidate.id(),
                candidate.brief().targetLanguage());
        return Result.ok(new ProjectChange.Saved(candidate));
    }
}

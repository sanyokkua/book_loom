package ua.bookloom.pipeline.project;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.ProjectChange;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.NameOrigin;
import ua.bookloom.api.project.Project;

/** The automatic project name and the clashes inside one book; pure, it reads no repository. */
// Checkstyle reads the source before Lombok adds the private constructor (see HashUtil).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ProjectNames {

    static String automatic(final String title, final @Nullable String source, final @Nullable String target) {
        return Project.automaticName(title, source, target);
    }

    static String titleOf(final @Nullable String metadataTitle, final Path source) {
        if (metadataTitle != null) {
            final int colon = metadataTitle.indexOf(':');
            final String head = (colon < 0 ? metadataTitle : metadataTitle.substring(0, colon)).strip();
            if (!head.isEmpty()) {
                return head;
            }
        }
        final String fileName = String.valueOf(source.getFileName());
        final int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    // A copy for another target: no model-bound state, an automatic name unless the person typed one.
    static Project translatedCopy(
            final Project source,
            final String id,
            final String target,
            final @Nullable String typedName,
            final Instant now) {
        final BookBrief brief = source.brief().withLanguages(source.brief().sourceLanguage(), target);
        final String typedText = typedName == null ? "" : typedName.strip();
        final boolean typed = !typedText.isEmpty();
        final String name = typed ? typedText : automatic(source.title(), brief.sourceLanguage(), target);
        return new Project(
                id,
                source.source(),
                source.format(),
                source.contentHash(),
                brief,
                name,
                typed ? NameOrigin.USER : NameOrigin.AUTO,
                source.title(),
                now,
                now);
    }

    static Optional<Project> reusable(
            final String contentHash, final @Nullable String source, final List<Project> stored) {
        return stored.stream()
                .filter(project -> project.contentHash().equals(contentHash))
                .filter(project -> project.nameOrigin() == NameOrigin.AUTO)
                .filter(project -> project.brief().targetLanguage() == null)
                .filter(project -> Objects.equals(project.brief().sourceLanguage(), source))
                .max(Comparator.comparing(Project::lastOpenedAt));
    }

    static Optional<ProjectChange> conflictOf(final Project candidate, final List<Project> stored) {
        Objects.requireNonNull(candidate, "candidate");
        final List<Project> others = stored.stream()
                .filter(project -> project.contentHash().equals(candidate.contentHash()))
                .filter(project -> !project.id().equals(candidate.id()))
                .toList();
        final Optional<ProjectChange> pair = pairClash(candidate, others);
        return pair.isPresent() ? pair : nameClash(candidate, others);
    }

    private static Optional<ProjectChange> pairClash(final Project candidate, final List<Project> others) {
        final String target = candidate.brief().targetLanguage();
        if (candidate.nameOrigin() != NameOrigin.AUTO || target == null) {
            return Optional.empty();
        }
        return others.stream()
                .filter(project -> sameValue(
                        project.brief().sourceLanguage(), candidate.brief().sourceLanguage()))
                .filter(project -> target.equals(project.brief().targetLanguage()))
                .findFirst()
                .map(ProjectChange.PairInUse::new);
    }

    private static Optional<ProjectChange> nameClash(final Project candidate, final List<Project> others) {
        return others.stream()
                .filter(project -> project.nameKey().equals(candidate.nameKey()))
                .findFirst()
                .map(ProjectChange.NameInUse::new);
    }

    private static boolean sameValue(final @Nullable String left, final @Nullable String right) {
        return Objects.equals(left, right);
    }
}

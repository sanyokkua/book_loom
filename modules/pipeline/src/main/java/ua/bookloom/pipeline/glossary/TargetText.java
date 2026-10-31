package ua.bookloom.pipeline.glossary;

import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Project;
import ua.bookloom.pipeline.prompt.LanguageRules;
import ua.bookloom.util.text.StressMarks;

/**
 * The one way a target enters the glossary or the lexicon — a model's suggestion, a CSV row, a typed edit, a learned
 * rendering: without the stress marks the target language's file asks to remove ({@code stripStressMarks=true}), so a
 * stored spelling always matches the book's text (design D4). A language that does not ask (Bulgarian writes a grave
 * on {@code ѝ}) keeps its marks.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TargetText {

    private static final String STRIP_KEY = "stripStressMarks";

    /**
     * The target as it is stored.
     *
     * @param target the target as it came in, or null for none
     * @param targetLanguage the project's target language tag, or null when none is chosen yet
     * @return the target without its stress marks when the language asks for that; the target unchanged otherwise,
     *     null for null
     */
    public static @Nullable String clean(@Nullable final String target, @Nullable final String targetLanguage) {
        if (target == null || targetLanguage == null || !stripsStress(targetLanguage)) {
            return target;
        }
        final int marks = StressMarks.count(target);
        if (marks == 0) {
            return target;
        }
        log.debug("Target stress marks removed language={} marks={}", targetLanguage, marks);
        return StressMarks.strip(target);
    }

    /**
     * Whether the target language's file asks for stress marks to be removed.
     *
     * @param targetLanguage the non-null target language tag
     * @return {@code true} if its file says {@code stripStressMarks=true}, {@code false} otherwise
     */
    static boolean stripsStress(final String targetLanguage) {
        Objects.requireNonNull(targetLanguage, "targetLanguage");
        return LanguageRules.bundled().wordsOf(targetLanguage, STRIP_KEY).stream()
                .anyMatch("true"::equalsIgnoreCase);
    }

    /**
     * The target language a project's brief chose.
     *
     * @param projects where the project is stored; never null
     * @param projectId the non-null project id
     * @return the tag, or null when the project, its brief or its target language is not there
     */
    public static @Nullable String targetLanguageOf(final ProjectRepository projects, final String projectId) {
        Objects.requireNonNull(projects, "projects");
        Objects.requireNonNull(projectId, "projectId");
        return Optional.ofNullable(projects.find(projectId).data())
                .flatMap(project -> project.map(Project::brief))
                .map(BookBrief::targetLanguage)
                .orElse(null);
    }
}

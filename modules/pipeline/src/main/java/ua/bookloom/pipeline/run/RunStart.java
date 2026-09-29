package ua.bookloom.pipeline.run;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.project.Project;

/**
 * The refusals that stop a run before any model call: a target or source language that is not a language code, an
 * unknown project, and a project whose book is no longer open. The target language becomes part of the output file
 * name, so a value such as {@code ../x} must never get past here.
 */
// Checkstyle parses source text before Lombok's annotation processor creates the private constructor,
// so suppress only its source-level utility-constructor false positive.
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public final class RunStart {

    private static final Pattern LANGUAGE_CODE = Pattern.compile("^[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})*$");

    /**
     * What a run starts from.
     *
     * @param project the stored project, with the brief as it is now
     * @param document the project's opened book
     */
    public record Started(Project project, Document document) {

        /** Rejects a missing component. */
        public Started {
            Objects.requireNonNull(project, "project");
            Objects.requireNonNull(document, "document");
        }
    }

    /**
     * Checks that a run of the project can start.
     *
     * @param stores the non-null stores to read the project from
     * @param projectId the non-null id of the project to run
     * @return what the run starts from, or {@code validation} naming the check that failed, before any model call
     */
    public static Result<Started> check(final RunStores stores, final String projectId) {
        Objects.requireNonNull(stores, "stores");
        Objects.requireNonNull(projectId, "projectId");
        try {
            log.debug("Checking that a run can start project={}", projectId);
            final Result<Optional<Project>> found = stores.projects().find(projectId);
            if (found.isErr()) {
                return Result.err(Objects.requireNonNull(found.error(), "error"));
            }
            final Optional<Project> project = Objects.requireNonNull(found.data(), "found");
            return project.isEmpty()
                    ? Result.err(refusal("project", projectId, "This project is not known", "Import the book again."))
                    : checkProject(stores, project.get());
        } catch (Throwable cause) {
            log.error("Unexpected failure while checking a run project={}", projectId, cause);
            return Result.err(AppError.of(
                    ErrorCode.internal,
                    "This run could not be started",
                    "An unexpected error stopped the run before it began.",
                    null,
                    cause));
        }
    }

    private static Result<Started> checkProject(final RunStores stores, final Project project) {
        final AppError languages = languageRefusal(project);
        if (languages != null) {
            return Result.err(languages);
        }
        final Document document = stores.openProjects().get(project.id());
        if (document == null) {
            return Result.err(refusal(
                    "book-open",
                    project.id(),
                    "This project is not open",
                    "No open project has this id; open the book again."));
        }
        log.debug("Run start checks passed project={}", project.id());
        return Result.ok(new Started(project, document));
    }

    private static @Nullable AppError languageRefusal(final Project project) {
        final String target = project.brief().targetLanguage();
        if (target == null) {
            return refusal("target-language", "none", "No target language is chosen", "Choose the target language.");
        }
        final AppError targetRefusal = codeRefusal("target-language", target);
        final String source = project.brief().sourceLanguage();
        return targetRefusal != null || source == null ? targetRefusal : codeRefusal("source-language", source);
    }

    private static @Nullable AppError codeRefusal(final String check, final String language) {
        final boolean valid = LANGUAGE_CODE.matcher(language).matches();
        log.debug("Run start check={} value={} outcome={}", check, language, valid ? "passed" : "failed");
        return valid
                ? null
                : refusal(
                        check,
                        language,
                        "This language code is not valid",
                        "Use two or three letters followed only by optional hyphenated language subtags.");
    }

    private static AppError refusal(final String check, final String value, final String title, final String message) {
        log.warn("Refused run check={} value={} code={}", check, value, ErrorCode.validation);
        return AppError.of(ErrorCode.validation, title, message);
    }
}

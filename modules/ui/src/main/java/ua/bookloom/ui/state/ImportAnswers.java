package ua.bookloom.ui.state;

import java.nio.file.Path;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.ImportedBook;

/** Turns what the project service answered for an import into one checked answer or one error, off the FX thread. */
// Checkstyle parses source text before Lombok runs, so it cannot see the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ImportAnswers {

    /** A checked answer: the book file and what the service made of it. */
    record Answer(Path source, ImportedBook imported) {}

    // A refusal and a malformed answer both become errors here, so the FX thread only routes by code.
    static Result<Answer> answerOf(final Path source, final Result<ImportedBook> answer) {
        final AppError failure = answer.error();
        if (failure != null) {
            return Result.err(failure);
        }
        final ImportedBook imported = Objects.requireNonNull(answer.data(), "imported book");
        final String projectId = imported.projectId();
        if (projectId != null
                && (imported.brief() == null || imported.inspection().format() == null)) {
            log.error("project {} was answered without its brief or format", projectId);
            return Result.err(internalError(null));
        }
        return Result.ok(new Answer(source, imported));
    }

    static AppError internalError(final @Nullable Throwable cause) {
        return AppError.of(
                ErrorCode.internal,
                "Unexpected error",
                "The book could not be opened because of an unexpected error.",
                null,
                cause);
    }
}

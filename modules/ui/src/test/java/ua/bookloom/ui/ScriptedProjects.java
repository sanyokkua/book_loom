package ua.bookloom.ui;

import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.NameOrigin;
import ua.bookloom.api.project.Project;

/** Shared pieces of {@link ScriptedProjectService}: the stored project it lists, its gate wait and its default answer. */
// Checkstyle reads the source before Lombok adds the private constructor (see HashUtil).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ScriptedProjects {

    private static final long WAIT_SECONDS = 10;

    static Project projectOf(final String id, final Path source) {
        return new Project(
                id,
                source.toAbsolutePath().normalize(),
                BookFormat.TXT,
                "hash",
                BookBrief.defaults("en"),
                "Book",
                NameOrigin.AUTO,
                "Book",
                Instant.EPOCH,
                Instant.EPOCH);
    }

    static void awaitLatch(final CountDownLatch latch, final String message) throws InterruptedException {
        if (!latch.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
            throw new IllegalStateException(message);
        }
    }

    static void awaitGate(final @Nullable CountDownLatch held) {
        if (held == null) {
            return;
        }
        try {
            if (!held.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("the test never released the service");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while held back", e);
        }
    }

    static <T> Result<T> notScripted() {
        return Result.err(
                AppError.of(ErrorCode.internal, "Not scripted", "The scripted project service has no answer."));
    }
}

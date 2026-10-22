package ua.bookloom.ui;

import com.google.inject.Binder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/** Small helpers {@link UiTestInjector} binds its graph with. */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class UiTestBindings {

    // Two, so that a held provider check and a held model listing never block each other.
    private static final int BACKGROUND_THREADS = 2;

    static <T> void bindIfSet(final Binder binder, final Class<T> type, final @Nullable T replacement) {
        if (replacement != null) {
            binder.bind(type).toInstance(replacement);
        }
    }

    static ExecutorService daemonExecutor() {
        return Executors.newFixedThreadPool(BACKGROUND_THREADS, task -> {
            final Thread thread = new Thread(task, "ui-test-background");
            thread.setDaemon(true);
            return thread;
        });
    }
}

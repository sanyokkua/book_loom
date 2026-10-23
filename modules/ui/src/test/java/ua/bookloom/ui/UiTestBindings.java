package ua.bookloom.ui;

import com.google.inject.Binder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ContextLength;
import ua.bookloom.api.llm.ModelCapabilities;

/** Small helpers {@link UiTestInjector} binds its graph with. */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class UiTestBindings {

    // Two, so that a held provider check and a held model listing never block each other.
    private static final int BACKGROUND_THREADS = 2;

    /** A provider that never says what window its model has, so a run sizes against its default. */
    static final ModelCapabilities UNKNOWN_CONTEXT = (providerId, modelId) -> Result.ok(ContextLength.unknown());

    static <T> void bindIfSet(final Binder binder, final Class<T> type, final @Nullable T replacement) {
        if (replacement != null) {
            binder.bind(type).toInstance(replacement);
        }
    }

    /** Binds the pool behind {@code @BackgroundExecutor} (the given one, else a daemon pool) and the I/O pool. */
    static void bindExecutors(final Binder binder, final @Nullable ExecutorService background) {
        binder.bind(ExecutorService.class)
                .annotatedWith(BackgroundExecutor.class)
                .toInstance(background != null ? background : daemonExecutor());
        binder.bind(ExecutorService.class).annotatedWith(IoExecutor.class).toInstance(daemonExecutor());
    }

    static ExecutorService daemonExecutor() {
        return Executors.newFixedThreadPool(BACKGROUND_THREADS, task -> {
            final Thread thread = new Thread(task, "ui-test-background");
            thread.setDaemon(true);
            return thread;
        });
    }
}

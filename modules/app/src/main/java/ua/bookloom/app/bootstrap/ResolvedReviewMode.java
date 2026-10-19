package ua.bookloom.app.bootstrap;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.ReviewMode;

/**
 * Immutable result of resolving the review mode chosen at launch.
 *
 * @param mode how much a person confirms during a run
 * @param source where the mode came from; {@link Source#DEFAULT} also when a configured value named no mode
 * @param rejectedValue the configured value that named no mode, or {@code null} when nothing was rejected
 */
public record ResolvedReviewMode(
        ReviewMode mode, Source source, @Nullable String rejectedValue) {

    /** The input that supplied the resolved mode. */
    public enum Source {
        ENVIRONMENT,
        PROPERTY,
        DEFAULT
    }

    /** Validates and freezes the resolver result. */
    public ResolvedReviewMode {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(source, "source");
    }
}

package ua.bookloom.app.bootstrap;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.ReviewMode;

/**
 * Purely resolves the review mode from injected environment and property lookups.
 *
 * <p>The flag is read once at launch; {@link ReviewModeSource} weighs it against the stored setting at each run start. A value that names no mode never
 * silently changes how a run behaves: it counts as set, keeps the input it came from, means Unattended, and travels on as data, so the launcher can say
 * once which text it refused.
 */
// Checkstyle parses source text before Lombok's annotation processor creates the private constructor,
// so suppress only its source-level utility-constructor false positive.
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ReviewModeResolver {

    private static final String ENVIRONMENT_MODE = "BOOKLOOM_REVIEW_MODE";
    private static final String PROPERTY_MODE = "bookloom.review.mode";

    /** Resolves the first configured mode, or Unattended when none is set or the configured one is unknown. */
    public static ResolvedReviewMode resolve(
            Function<String, @Nullable String> getEnv, Function<String, @Nullable String> getProperty) {
        Objects.requireNonNull(getEnv, "getEnv");
        Objects.requireNonNull(getProperty, "getProperty");

        final String envValue = getEnv.apply(ENVIRONMENT_MODE);
        if (envValue != null) {
            return resolveConfigured(envValue.trim(), ResolvedReviewMode.Source.ENVIRONMENT);
        }

        final String propertyValue = getProperty.apply(PROPERTY_MODE);
        if (propertyValue != null) {
            return resolveConfigured(propertyValue.trim(), ResolvedReviewMode.Source.PROPERTY);
        }

        return new ResolvedReviewMode(ReviewMode.UNATTENDED, ResolvedReviewMode.Source.DEFAULT, null);
    }

    private static ResolvedReviewMode resolveConfigured(String value, ResolvedReviewMode.Source source) {
        final ReviewMode mode = parse(value);
        if (mode != null) {
            return new ResolvedReviewMode(mode, source, null);
        }
        return new ResolvedReviewMode(ReviewMode.UNATTENDED, source, value);
    }

    private static @Nullable ReviewMode parse(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "unattended", "auto" -> ReviewMode.UNATTENDED;
            case "assisted", "semi-manual" -> ReviewMode.ASSISTED;
            case "manual" -> ReviewMode.MANUAL;
            default -> null;
        };
    }
}

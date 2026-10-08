package ua.bookloom.ui.state;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/** The one rule for when a brief's languages let the work go on: both chosen and different. */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class BriefLanguages {

    /**
     * Whether the languages are usable.
     *
     * @param source the declared source language tag, or {@code null} when none is set
     * @param target the chosen target language tag, or {@code null} when none is set
     * @return {@code true} if both are set and differ, {@code false} otherwise
     */
    static boolean isUsable(final @Nullable String source, final @Nullable String target) {
        return source != null && target != null && !source.equals(target);
    }
}

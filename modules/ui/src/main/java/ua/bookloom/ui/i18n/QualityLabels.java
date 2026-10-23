package ua.bookloom.ui.i18n;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.BookBrief;

/** The word for a brief's quality dial, shared by every screen that lists a project beside its model. */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class QualityLabels {

    /**
     * The catalogue entry naming the dial of a brief.
     *
     * @param brief the brief whose quality is named
     * @return the entry for Fast, Balanced or Max
     */
    public static MessageKey of(final BookBrief brief) {
        return of(Objects.requireNonNull(brief, "brief").dial());
    }

    /**
     * The catalogue entry naming a dial setting.
     *
     * @param dial the setting; never null
     * @return the entry for Fast, Balanced or Max
     */
    public static MessageKey of(final QualityDial dial) {
        return switch (Objects.requireNonNull(dial, "dial")) {
            case FAST -> MessageKey.BRIEF_QUALITY_FAST;
            case BALANCED -> MessageKey.BRIEF_QUALITY_BALANCED;
            case MAX -> MessageKey.BRIEF_QUALITY_MAX;
        };
    }
}

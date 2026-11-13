package ua.bookloom.pipeline.context;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.document.UnitRole;
import ua.bookloom.api.llm.ModelProfile;

/**
 * Builds a call's window from the book's reading order (design D8): the segments before the focus over the body units,
 * across chapter and unit boundaries, so the first paragraph of a chapter still sees the end of the one before; never
 * front or back matter or the auxiliary unit, whose text is the publisher's, not the story's.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class WindowAssembler {

    /** Previous pairs a small model sees: a few lines of context without crowding its window. */
    static final int PREVIOUS_PAIRS_SMALL = 2;

    /** Previous pairs a large model sees. */
    static final int PREVIOUS_PAIRS_LARGE = 3;

    /**
     * The window of one call.
     *
     * @param units the non-null units of the book in reading order
     * @param focus the non-null items the call works on, in reading order
     * @param profile the non-null profile whose tier sets how many previous pairs are shown
     * @param accepted the non-null reader of the earlier segments' kept targets
     * @return the window; the window of the focus alone when its first item is not in the body; the reader's error
     *     when a target cannot be read, or {@link ErrorCode#internal} when the window cannot be built
     */
    public static Result<Window> assemble(
            final List<Unit> units, final Focus focus, final ModelProfile profile, final AcceptedTargets accepted) {
        Objects.requireNonNull(units, "units");
        Objects.requireNonNull(focus, "focus");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(accepted, "accepted");
        try {
            return build(readingOrder(units), focus, previousPairs(profile.tier()), accepted, profile.tier());
        } catch (RuntimeException cause) {
            log.error("Window of {} items failed", focus.items().size(), cause);
            return Result.err(AppError.of(
                    ErrorCode.internal, "Window failed", "The call's window could not be built.", null, cause));
        }
    }

    /**
     * The story's segments in reading order: the body units' segments, the auxiliary unit left out.
     *
     * @param units the non-null units in reading order
     * @return never null; empty for a book with no body
     */
    static List<Segment> readingOrder(final List<Unit> units) {
        return units.stream()
                .filter(unit -> !unit.isAuxiliary() && unit.role() == UnitRole.BODY)
                .flatMap(unit -> unit.segments().stream())
                .toList();
    }

    static int previousPairs(final ModelProfile.Tier tier) {
        return switch (tier) {
            case SMALL -> PREVIOUS_PAIRS_SMALL;
            case LARGE -> PREVIOUS_PAIRS_LARGE;
        };
    }

    private static Result<Window> build(
            final List<Segment> reading,
            final Focus focus,
            final int count,
            final AcceptedTargets accepted,
            final ModelProfile.Tier tier) {
        final List<String> ids = focus.segmentIds();
        final int first = ids.isEmpty() ? -1 : indexOf(reading, ids.getFirst());
        if (first < 0) {
            log.debug("Window focus={} is not in the body; no neighbours", ids.isEmpty() ? null : ids.getFirst());
            return Result.ok(Window.of(focus));
        }
        final String unit = reading.get(first).unit();
        boolean crossedUnit = false;
        final List<WindowPair> previous = new ArrayList<>();
        for (int index = first - 1; index >= 0 && previous.size() < count; index--) {
            final Segment before = reading.get(index);
            final Result<Optional<String>> target = accepted.acceptedTarget(before.id());
            if (target.isErr()) {
                return Result.err(Objects.requireNonNull(target.error(), "error"));
            }
            final @Nullable String shown =
                    Objects.requireNonNull(target.data(), "target").orElse(null);
            previous.addFirst(new WindowPair(before.id(), before.masked(), shown));
            crossedUnit = crossedUnit || !before.unit().equals(unit);
        }
        final int last = Math.max(first, indexOf(reading, ids.getLast()));
        final @Nullable String next =
                last + 1 < reading.size() ? reading.get(last + 1).masked() : null;
        final Window window = new Window(previous, focus, next);
        logWindow(window, crossedUnit, tier);
        return Result.ok(window);
    }

    private static int indexOf(final List<Segment> reading, final String segmentId) {
        for (int index = 0; index < reading.size(); index++) {
            if (reading.get(index).id().equals(segmentId)) {
                return index;
            }
        }
        return -1;
    }

    private static void logWindow(final Window window, final boolean crossedUnit, final ModelProfile.Tier tier) {
        final long sourceOnly = window.previous().stream()
                .filter(pair -> pair.maskedTarget() == null)
                .count();
        log.debug(
                "Window focus={} previous={} sourceOnly={} crossedUnit={} next={} tier={}",
                window.focus().segmentIds().getFirst(),
                window.previous().size(),
                sourceOnly,
                crossedUnit,
                window.nextSource() != null,
                tier);
        if (log.isTraceEnabled()) {
            window.previous()
                    .forEach(pair -> log.trace(
                            "Window previous {} source={} target={}",
                            pair.segmentId(),
                            pair.maskedSource(),
                            pair.maskedTarget()));
            log.trace("Window next source={}", window.nextSource());
        }
    }
}

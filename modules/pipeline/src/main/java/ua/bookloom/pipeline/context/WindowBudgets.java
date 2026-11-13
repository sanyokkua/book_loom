package ua.bookloom.pipeline.context;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ModelProfile;
import ua.bookloom.pipeline.chunk.TokenEstimator;

/**
 * The token budgets of one translate-family call by the model's tier, and the order its optional context is dropped
 * in when the prompt is over them (design D8, "Budgets"). Small models translate worse as a prompt grows, so each tier
 * has a quality ceiling well below the window; the window itself bounds the prompt, its reserved reply and a margin.
 * What helps least goes first: the next source, the oldest earlier summaries, the oldest previous pairs, the map lines
 * the window names least, the last summary and pair, and only then batch items. The focus's first item, a pinned or
 * locked name and a character's stated gender are never dropped.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class WindowBudgets {

    /** The small tier's budgets; unmeasured. */
    public static final Shares SMALL = new Shares(300, 600, 150, 600, 3500);

    /** The large tier's budgets; unmeasured. */
    public static final Shares LARGE = new Shares(500, 1000, 250, 1200, 6000);

    /** The most source tokens one chapter-summary call reads on a small model (design D10); unmeasured. */
    public static final int CHAPTER_PART_TOKENS_SMALL = 2_500;

    /** The most source tokens one chapter-summary call reads on a large model (design D10); unmeasured. */
    public static final int CHAPTER_PART_TOKENS_LARGE = 4_500;

    private static final String DROP_NEXT = "next";
    private static final String DROP_SUMMARY = "summary";
    private static final String DROP_PREVIOUS = "previous";
    private static final String DROP_MAP = "map";
    private static final String DROP_BATCH = "batch";

    /**
     * The budgets of one tier, in estimated tokens.
     *
     * @param previous the most source tokens the previous pairs may hold; positive
     * @param currentPart the most source tokens a focus item should hold, above which a segment is split; positive
     * @param next the most tokens the next source may hold, else it is left out; positive
     * @param summaries the most tokens the earlier chapters' summaries may hold together; positive
     * @param promptCeiling the most tokens the whole prompt may hold; positive
     */
    public record Shares(int previous, int currentPart, int next, int summaries, int promptCeiling) {

        /** Rejects a budget that is not positive. */
        public Shares {
            if (previous <= 0 || currentPart <= 0 || next <= 0 || summaries <= 0 || promptCeiling <= 0) {
                throw new IllegalArgumentException("window budgets must be positive: previous=" + previous
                        + " currentPart=" + currentPart + " next=" + next + " summaries=" + summaries
                        + " ceiling=" + promptCeiling);
            }
        }
    }

    /**
     * What one call is fitted to.
     *
     * @param shares the tier's budgets
     * @param contextWindow the run's window in tokens; positive
     * @param sourceLanguage the language of the book's text, or null when unknown
     * @param targetLanguage the language the summaries are written in, or null when unknown
     */
    public record Budget(
            Shares shares,
            int contextWindow,
            @Nullable String sourceLanguage,
            @Nullable String targetLanguage) {

        /** Rejects missing budgets or a window that is not positive. */
        public Budget {
            Objects.requireNonNull(shares, "shares");
            if (contextWindow <= 0) {
                throw new IllegalArgumentException("contextWindow must be positive: " + contextWindow);
            }
        }
    }

    /**
     * One call's context after fitting.
     *
     * @param window the window that is sent: the previous pairs, the kept items and the next source left
     * @param lines the map lines that are sent
     * @param summaries the earlier chapters' summaries that are sent, oldest first
     * @param keptItems how many of the focus items the call keeps, from the first; at least one when any was given
     * @param promptTokens the estimated prompt
     * @param reserve the output the kept items reserve
     */
    public record Fitted(
            Window window, MapLines lines, List<String> summaries, int keptItems, int promptTokens, int reserve) {

        /** Copies the summaries. */
        public Fitted {
            Objects.requireNonNull(window, "window");
            Objects.requireNonNull(lines, "lines");
            summaries = List.copyOf(Objects.requireNonNull(summaries, "summaries"));
        }
    }

    /** Renders and measures a candidate, so the budgets fit what is actually sent. */
    public interface Sizer {

        /**
         * The map lines the window names, with their counts.
         *
         * @param window the non-null candidate window
         * @return never null
         */
        List<MapEntry> map(Window window);

        /**
         * The estimated prompt of a candidate.
         *
         * @param window the non-null candidate window
         * @param lines the non-null map lines
         * @param summaries the non-null earlier summaries
         * @return the tokens of the whole prompt, the template overhead included
         */
        int promptTokens(Window window, MapLines lines, List<String> summaries);

        /**
         * The output the focus reserves.
         *
         * @param focus the non-null candidate focus
         * @return the reserve in tokens
         */
        int reserve(Focus focus);
    }

    /** A candidate's droppable state: its window, its summaries and the map lines already dropped. */
    private record Candidate(Window window, List<String> summaries, Set<String> dropped) {

        Candidate withWindow(final List<WindowPair> previous, final Focus focus, @Nullable final String next) {
            return new Candidate(new Window(previous, focus, next), summaries, dropped);
        }
    }

    /** One step of the drop order: what went and the candidate it left. */
    private record Drop(String what, Candidate left) {}

    /**
     * The budgets of a tier.
     *
     * @param tier the non-null tier
     * @return {@link #SMALL} or {@link #LARGE}
     */
    public static Shares of(final ModelProfile.Tier tier) {
        Objects.requireNonNull(tier, "tier");
        return switch (tier) {
            case SMALL -> SMALL;
            case LARGE -> LARGE;
        };
    }

    /**
     * The current-part budget of a tier, above which one segment is split at sentence boundaries.
     *
     * @param tier the non-null tier
     * @return the budget in source tokens
     */
    public static int currentPart(final ModelProfile.Tier tier) {
        return of(tier).currentPart();
    }

    /**
     * The chapter-part budget of a tier, above which a finished chapter is summarised in consecutive parts.
     *
     * @param tier the non-null tier
     * @return the budget in source tokens
     */
    public static int chapterPart(final ModelProfile.Tier tier) {
        Objects.requireNonNull(tier, "tier");
        return switch (tier) {
            case SMALL -> CHAPTER_PART_TOKENS_SMALL;
            case LARGE -> CHAPTER_PART_TOKENS_LARGE;
        };
    }

    /**
     * Fits one call's context: each part first to its own share, then the whole prompt, its reserve and the safety
     * margin to the window and the tier's ceiling, one drop at a time, measuring again after each.
     *
     * @param budget the non-null budgets and window
     * @param window the non-null window as assembled
     * @param summaries the non-null earlier chapters' summaries, oldest first
     * @param sizer the non-null renderer the candidates are measured with
     * @return the fitted context; over its budget only when nothing more may be dropped
     */
    public static Fitted fit(
            final Budget budget, final Window window, final List<String> summaries, final Sizer sizer) {
        Objects.requireNonNull(budget, "budget");
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(summaries, "summaries");
        Objects.requireNonNull(sizer, "sizer");
        log.debug(
                "Fitting window previous={} items={} next={} summaries={} window={} ceiling={}",
                window.previous().size(),
                window.focus().items().size(),
                window.nextSource() != null,
                summaries.size(),
                budget.contextWindow(),
                budget.shares().promptCeiling());
        Candidate candidate = toShares(budget, window, summaries);
        Fitted measured = measure(candidate, sizer);
        while (!fits(budget, measured)) {
            final Optional<Drop> drop = nextDrop(candidate, sizer);
            if (drop.isEmpty()) {
                break;
            }
            candidate = drop.get().left();
            final Fitted after = measure(candidate, sizer);
            logDrop(drop.get().what(), measured.promptTokens(), after, budget);
            measured = after;
        }
        return finish(budget, measured);
    }

    private static Fitted measure(final Candidate candidate, final Sizer sizer) {
        final List<MapEntry> entries = kept(candidate, sizer);
        final MapLines lines = MapLines.of(entries);
        final Window window = candidate.window();
        return new Fitted(
                window,
                lines,
                candidate.summaries(),
                window.focus().items().size(),
                sizer.promptTokens(window, lines, candidate.summaries()),
                sizer.reserve(window.focus()));
    }

    // The window's lines are chosen again for every candidate, since its visible sources change, minus those dropped.
    private static List<MapEntry> kept(final Candidate candidate, final Sizer sizer) {
        return sizer.map(candidate.window()).stream()
                .filter(entry -> !candidate.dropped().contains(entry.line()))
                .toList();
    }

    private static boolean fits(final Budget budget, final Fitted measured) {
        return measured.promptTokens() <= budget.shares().promptCeiling()
                && measured.promptTokens() + measured.reserve() + ContextBudget.SAFETY_MARGIN <= budget.contextWindow();
    }

    private static Candidate toShares(final Budget budget, final Window window, final List<String> summaries) {
        final Shares shares = budget.shares();
        final List<WindowPair> previous = new ArrayList<>(window.previous());
        while (!previous.isEmpty() && sourceTokens(previous, budget) > shares.previous()) {
            log.debug(
                    "fit share previous={} over {}: drop {}",
                    sourceTokens(previous, budget),
                    shares.previous(),
                    previous.getFirst().segmentId());
            previous.removeFirst();
        }
        @Nullable String next = window.nextSource();
        if (next != null && TokenEstimator.estimate(next, budget.sourceLanguage()) > shares.next()) {
            log.debug("fit share next over {}: dropped", shares.next());
            next = null;
        }
        final List<String> kept = new ArrayList<>(summaries);
        while (!kept.isEmpty() && summaryTokens(kept, budget) > shares.summaries()) {
            log.debug(
                    "fit share summaries={} over {}: drop the oldest", summaryTokens(kept, budget), shares.summaries());
            kept.removeFirst();
        }
        final @Nullable String nextTarget = next == null ? null : window.nextTarget();
        return new Candidate(new Window(previous, window.focus(), next, nextTarget), List.copyOf(kept), Set.of());
    }

    private static int sourceTokens(final List<WindowPair> pairs, final Budget budget) {
        return pairs.stream()
                .mapToInt(pair -> TokenEstimator.estimate(pair.maskedSource(), budget.sourceLanguage()))
                .sum();
    }

    private static int summaryTokens(final List<String> summaries, final Budget budget) {
        return summaries.stream()
                .mapToInt(summary -> TokenEstimator.estimate(summary, budget.targetLanguage()))
                .sum();
    }

    private static Optional<Drop> nextDrop(final Candidate candidate, final Sizer sizer) {
        final Window window = candidate.window();
        final List<WindowPair> previous = window.previous();
        if (window.nextSource() != null) {
            return drop(DROP_NEXT, candidate.withWindow(previous, window.focus(), null));
        }
        if (candidate.summaries().size() > 1) {
            return drop(
                    DROP_SUMMARY,
                    withSummaries(
                            candidate,
                            candidate
                                    .summaries()
                                    .subList(1, candidate.summaries().size())));
        }
        if (previous.size() > 1) {
            return drop(
                    DROP_PREVIOUS, candidate.withWindow(previous.subList(1, previous.size()), window.focus(), null));
        }
        final Optional<MapEntry> least = kept(candidate, sizer).stream()
                .filter(entry -> !entry.kept())
                .min(Comparator.comparingInt(MapEntry::occurrences));
        if (least.isPresent()) {
            return drop(DROP_MAP, withDropped(candidate, least.get().line()));
        }
        return lastDrop(candidate);
    }

    private static Optional<Drop> lastDrop(final Candidate candidate) {
        final Window window = candidate.window();
        if (!candidate.summaries().isEmpty()) {
            return drop(DROP_SUMMARY, withSummaries(candidate, List.of()));
        }
        if (!window.previous().isEmpty()) {
            return drop(DROP_PREVIOUS, candidate.withWindow(List.of(), window.focus(), null));
        }
        final List<FocusItem> items = window.focus().items();
        if (items.size() > 1) {
            final Focus fewer =
                    new Focus(items.subList(0, items.size() - 1), window.focus().reasons());
            return drop(DROP_BATCH, candidate.withWindow(List.of(), fewer, null));
        }
        log.debug("fit: nothing left to drop");
        return Optional.empty();
    }

    private static Candidate withSummaries(final Candidate candidate, final List<String> summaries) {
        return new Candidate(candidate.window(), List.copyOf(summaries), candidate.dropped());
    }

    private static Candidate withDropped(final Candidate candidate, final String line) {
        final Set<String> dropped = new HashSet<>(candidate.dropped());
        dropped.add(line);
        return new Candidate(candidate.window(), candidate.summaries(), Set.copyOf(dropped));
    }

    private static Optional<Drop> drop(final String what, final Candidate left) {
        return Optional.of(new Drop(what, left));
    }

    private static Fitted finish(final Budget budget, final Fitted fitted) {
        log.debug(
                "fit done promptTokens={} reserve={} margin={} ceiling={} window={} items={} previous={} next={}",
                fitted.promptTokens(),
                fitted.reserve(),
                ContextBudget.SAFETY_MARGIN,
                budget.shares().promptCeiling(),
                budget.contextWindow(),
                fitted.keptItems(),
                fitted.window().previous().size(),
                fitted.window().nextSource() != null);
        if (!fits(budget, fitted)) {
            log.warn(
                    "Prompt still over its budget after every drop promptTokens={} reserve={} ceiling={} window={}",
                    fitted.promptTokens(),
                    fitted.reserve(),
                    budget.shares().promptCeiling(),
                    budget.contextWindow());
        }
        fitted.window().focus().items().stream()
                .filter(item -> TokenEstimator.estimate(item.maskedSource(), budget.sourceLanguage())
                        > budget.shares().currentPart())
                .forEach(item -> log.warn(
                        "Segment {} is over the current-part budget of {} tokens",
                        item.segmentId(),
                        budget.shares().currentPart()));
        return fitted;
    }

    private static void logDrop(final String what, final int before, final Fitted after, final Budget budget) {
        log.debug(
                "fit drop={} before={} after={} ceiling={}",
                what,
                before,
                after.promptTokens(),
                budget.shares().promptCeiling());
    }
}

package ua.bookloom.pipeline.proofread;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.ProofreadVerdict;
import ua.bookloom.pipeline.context.BookMap;
import ua.bookloom.pipeline.context.LexiconFilter;
import ua.bookloom.pipeline.context.MapLines;
import ua.bookloom.pipeline.context.WindowMap;
import ua.bookloom.util.hash.HashUtil;

/**
 * The fingerprint a proofread verdict keeps of what its window read (design D15 "Resume"): the focus segments' sources,
 * current texts and reasons, the neighbours' texts, the map lines the window names, the narrator's person and gender,
 * the proofread prompt's version and the round, so the same fingerprint means the same question and a window holding it
 * is not read again. It is taken with no planner, so the stored project's tally takes the very same one a run takes.
 * The round is kept readable in front of the hash, so Max's whole-book reading can tell its own verdicts from the first
 * round's.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class WindowFingerprint {

    /** The version of the proofread prompt; raised whenever its wording changes, so every window is read again. */
    static final int PROMPT_VERSION = 1;

    /** The round of the marked and doubtful windows. */
    static final int ROUND_MARKED = 1;

    /** Max's whole-book round. */
    static final int ROUND_WHOLE_BOOK = 2;

    private static final char SEPARATOR = '\1';
    private static final String LIST_SEPARATOR = "\2";
    private static final String ROUND_PREFIX = "r";
    private static final char ROUND_END = ':';

    /**
     * Fingerprints one window.
     *
     * @param round the round the window is read in, {@link #ROUND_MARKED} or {@link #ROUND_WHOLE_BOOK}
     * @param window the non-null window, whose reasons block 13 sends
     * @param texts the non-null texts of the window's focus and neighbours, as they stand now
     * @param map the non-null book map as it stands now
     * @param brief the non-null Book Brief as stored now, whose languages and narrator the call reads
     * @return {@code r<round>:} and the lowercase hex SHA-256 of the question
     */
    static String of(
            final int round,
            final ProofreadWindow window,
            final ProofreadCall.Texts texts,
            final BookMap map,
            final BookBrief brief) {
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(texts, "texts");
        final List<String> parts = new ArrayList<>();
        window.focusIds().forEach(id -> {
            parts.add(texts.source(id));
            parts.add(texts.current(id));
            parts.add(String.join(LIST_SEPARATOR, window.reasons().getOrDefault(id, List.of())));
        });
        parts.add(neighbour(window.beforeId(), texts));
        parts.add(neighbour(window.afterId(), texts));
        parts.add(mapLines(window, texts, Objects.requireNonNull(map, "map"), brief));
        final Narrator narrator = Objects.requireNonNull(brief, "brief").narrator();
        parts.add(narrator.person().name());
        parts.add(narrator.gender().name());
        final StringBuilder question =
                new StringBuilder().append(PROMPT_VERSION).append(SEPARATOR).append(round);
        parts.forEach(part -> question.append(SEPARATOR).append(part));
        final String fingerprint = ROUND_PREFIX + round + ROUND_END + HashUtil.sha256OfNfcText(question.toString());
        log.trace("Proofread window {} fingerprint {} round={}", window.label(), fingerprint, round);
        return fingerprint;
    }

    /**
     * Whether a window was already read with the question it has now: every focus segment holds a verdict with this
     * fingerprint, or — for a first-round window — a verdict of Max's whole-book round, which read it after the first
     * round and is checked by its own fingerprint there. The run and the stored project's tally both ask this.
     *
     * @param fingerprint the non-null fingerprint the window has now
     * @param window the non-null window
     * @param verdicts the stored verdict of a segment by id, null for a segment that holds none
     * @return {@code true} if the window needs no call, {@code false} otherwise
     */
    static boolean isRead(
            final String fingerprint,
            final ProofreadWindow window,
            final Function<String, @Nullable ProofreadVerdict> verdicts) {
        Objects.requireNonNull(fingerprint, "fingerprint");
        final boolean firstRound = fingerprint.startsWith(ROUND_PREFIX + ROUND_MARKED + ROUND_END);
        return window.focusIds().stream().allMatch(id -> {
            final @Nullable ProofreadVerdict verdict = verdicts.apply(id);
            return verdict != null
                    && (verdict.fingerprint().equals(fingerprint)
                            || (firstRound && isWholeBook(verdict.fingerprint())));
        });
    }

    /**
     * Whether a stored fingerprint comes from Max's whole-book round, which read every paragraph after the marked
     * windows and so covers a first-round window of the same segment.
     *
     * @param fingerprint the non-null stored fingerprint
     * @return {@code true} if it was taken in the whole-book round, {@code false} otherwise
     */
    static boolean isWholeBook(final String fingerprint) {
        return Objects.requireNonNull(fingerprint, "fingerprint")
                .startsWith(ROUND_PREFIX + ROUND_WHOLE_BOOK + ROUND_END);
    }

    // The map lines the window's texts name before the budgets fit the call, so no model profile changes them.
    private static String mapLines(
            final ProofreadWindow window, final ProofreadCall.Texts texts, final BookMap map, final BookBrief brief) {
        final @Nullable String target = brief.targetLanguage();
        final MapLines lines = WindowMap.select(
                ProofreadCall.contextWindow(window, texts),
                List.of(),
                map,
                List.of(),
                LexiconFilter.of(brief.sourceLanguage(), target),
                target);
        return String.join(
                String.valueOf(SEPARATOR),
                String.join(LIST_SEPARATOR, lines.glossary()),
                String.join(LIST_SEPARATOR, lines.locked()),
                String.join(LIST_SEPARATOR, lines.suggested()),
                String.join(LIST_SEPARATOR, lines.lexicon()));
    }

    private static String neighbour(final @Nullable String id, final ProofreadCall.Texts texts) {
        return id == null ? "" : texts.current(id);
    }
}

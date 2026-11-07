package ua.bookloom.pipeline.proofread;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.util.hash.HashUtil;

/**
 * The fingerprint a proofread verdict keeps of what its window read (design D15 "Resume"): the focus segments' sources
 * and current texts, the neighbours' texts, the map lines the call carried, the proofread prompt's version and the
 * round, so the same fingerprint means the same question and a window holding it is not read again. The round is kept
 * readable in front of the hash, so Max's whole-book reading can tell its own verdicts from the first round's.
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
    private static final String ROUND_PREFIX = "r";
    private static final char ROUND_END = ':';

    /**
     * Fingerprints one window.
     *
     * @param round the round the window is read in, {@link #ROUND_MARKED} or {@link #ROUND_WHOLE_BOOK}
     * @param window the non-null window
     * @param texts the non-null texts of the window's focus and neighbours, as they stand now
     * @param mapLines the non-null map lines the window's call carries; empty when it carries none
     * @return {@code r<round>:} and the lowercase hex SHA-256 of the question
     */
    static String of(
            final int round, final ProofreadWindow window, final ProofreadCall.Texts texts, final String mapLines) {
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(texts, "texts");
        Objects.requireNonNull(mapLines, "mapLines");
        final List<String> parts = new ArrayList<>();
        window.focusIds().forEach(id -> {
            parts.add(texts.source(id));
            parts.add(texts.current(id));
        });
        parts.add(neighbour(window.beforeId(), texts));
        parts.add(neighbour(window.afterId(), texts));
        parts.add(mapLines);
        final StringBuilder question =
                new StringBuilder().append(PROMPT_VERSION).append(SEPARATOR).append(round);
        parts.forEach(part -> question.append(SEPARATOR).append(part));
        final String fingerprint = ROUND_PREFIX + round + ROUND_END + HashUtil.sha256OfNfcText(question.toString());
        log.trace("Proofread window {} fingerprint {} round={}", window.label(), fingerprint, round);
        return fingerprint;
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

    private static String neighbour(final @Nullable String id, final ProofreadCall.Texts texts) {
        return id == null ? "" : texts.current(id);
    }
}

package ua.bookloom.pipeline.proofread;

import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.util.hash.HashUtil;

/**
 * The hash a proofread verdict keeps of what its window read (design D15 "Resume"): the focus segments' sources and
 * current texts, the neighbours' texts, the proofread prompt's version and the round, so the same hash means the same
 * question. Task 13.6 adds the injected map lines and reads it to skip a window already read.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class WindowFingerprint {

    /** The version of the proofread prompt; raised whenever its wording changes, so every window is read again. */
    static final int PROMPT_VERSION = 1;

    private static final char SEPARATOR = '\1';

    /**
     * Fingerprints one window.
     *
     * @param round the round the window is read in, 1 for the marked windows
     * @param parts the window's texts in a fixed order: each focus segment's source and current text, then the
     *     neighbours' texts
     * @return the lowercase hex SHA-256 of the question
     */
    static String of(final int round, final List<String> parts) {
        final StringBuilder question =
                new StringBuilder().append(PROMPT_VERSION).append(SEPARATOR).append(round);
        parts.forEach(part -> question.append(SEPARATOR).append(part));
        final String hash = HashUtil.sha256OfNfcText(question.toString());
        log.trace("Proofread window fingerprint {} round={} parts={}", hash, round, parts.size());
        return hash;
    }
}

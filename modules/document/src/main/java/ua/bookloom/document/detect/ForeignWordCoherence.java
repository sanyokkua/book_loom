package ua.bookloom.document.detect;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * The single-byte-candidate coherence preference {@link CharsetLadder}'s detection rung applies once a match
 * clears {@code MINIMUM_USABLE_CONFIDENCE} — split out of {@link CharsetLadder} so that class stays a ladder,
 * not a scoring engine.
 *
 * <p><strong>Why this is needed, and why a whole-file re-rank is not enough.</strong> Every single-byte charset
 * decodes every byte without error, so a decode attempt is never evidence. A file that is mostly one language's
 * prose with a small foreign-script region (three surveyed books: Western European prose with a little Russian
 * site boilerplate) can be genuinely mixed rather than merely dominated: measured on the real corpus, one
 * 1.4&nbsp;MB book carries 22,298 non-ASCII bytes, of which ~22,100 are smart-punctuation bytes that decode to
 * the <em>same</em> code point under every Western European single-byte candidate and so vote for none of them,
 * and only ~195 are the Cyrillic region — swamped roughly 114 to 1. ICU's {@code detectAll()} over the whole
 * file does not list {@code windows-1251} at all in that case, so no re-ranking of ICU's whole-file candidate
 * list can ever reach it. The bytes that decode to unrelated characters instead render as e.g. {@code Ñïàñèáî}
 * instead of {@code Спасибо}, with no error raised anywhere.
 *
 * <p><strong>The test, evaluated only over the bytes that distinguish candidates.</strong>
 *
 * <ol>
 *   <li>Find the file's maximal runs of two or more consecutive bytes {@code >= 0x80} whose immediate
 *       neighbouring byte (if any) is not an ASCII letter — a "word" written entirely outside ASCII, as opposed
 *       to an accented letter sitting inside an otherwise-ASCII word like {@code café}. If there are none, the
 *       preference does not fire: a genuinely Western European file has no such runs, because its accented
 *       letters always sit inside ASCII words.
 *   <li>Detect over those runs, concatenated with a space between each, rather than over the whole file — this
 *       is what makes a candidate ICU never lists for the whole file reachable at all.
 *   <li>Build the candidate order: the region detection's single-byte candidates first, in its order, then any
 *       whole-file single-byte candidates not already present.
 *   <li>Score each candidate in that order — identified as single-byte exactly by
 *       {@code charset.canEncode() && charset.newEncoder().maxBytesPerChar() == 1.0f} — by the fraction of the
 *       foreign-word runs that decode, under it, to characters that are all letters and all of one
 *       {@link Character.UnicodeScript} that is not {@link Character.UnicodeScript#LATIN}.
 *   <li>Take the first candidate in that order achieving the maximal score. Among candidates sharing it, the
 *       order already carries the tie-break, so a wholly-Cyrillic file where {@code windows-1251} and
 *       {@code KOI8-R} both score 1.0 and region detection already ranks {@code windows-1251} first is left
 *       unchanged. A maximal score of zero leaves ICU's whole-file answer untouched.
 * </ol>
 *
 * <p><strong>Deliberately narrow.</strong> This applies only where {@link CharsetLadder}'s detection rung is
 * reached — a byte-order mark or an in-band declaration still outranks it, unchanged — and only re-ranks
 * charset candidates for bytes already known to be non-ASCII words. It is not a general "guess the language"
 * mechanism, and it must not pre-empt {@code add-metadata-units-and-language-detection}'s later, separate
 * language detection.
 *
 * <p><strong>And it fires only when the whole-file winner is itself single-byte.</strong> A UTF-8 multi-byte
 * sequence <em>is</em>, byte for byte, a run of two or more consecutive bytes {@code >= 0x80} — precisely the
 * foreign-word-run signature above — and under {@code windows-1251} those bytes frequently decode to
 * all-Cyrillic letters, scoring a perfect 1.0. Without this guard a UTF-8 file ICU identifies with confidence
 * 100 is overridden to {@code windows-1251}, and the corruption is <em>data-dependent</em>: one surveyed UTF-8
 * Markdown file flipped while another, equally Cyrillic, did not, because some of the second file's bytes happen
 * to land on {@code windows-1251} punctuation and so fail the all-letters test. If ICU's best whole-file answer
 * is a multi-byte encoding, there is no single-byte ambiguity to resolve and this preference has nothing to say.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ForeignWordCoherence {

    /**
     * The preferred single-byte charset for {@code fileBytes}, or {@code null} when the whole-file winner is not
     * itself single-byte, when no foreign-word run exists, or when no candidate decodes any run coherently — in
     * every one of those cases {@link CharsetLadder}'s whole-file top candidate is used unchanged.
     *
     * @param fileBytes the file's raw bytes
     * @param wholeFileCandidates every charset ICU considers possible over the whole file, best first
     * @return the preferred single-byte charset, or {@code null} to leave the whole-file winner unchanged
     */
    static @Nullable Charset prefer(byte[] fileBytes, List<CharsetLadder.Detection> wholeFileCandidates) {
        if (wholeFileCandidates.isEmpty()
                || !isSingleByte(wholeFileCandidates.get(0).charset())) {
            return null;
        }
        final List<int[]> foreignWordRuns = foreignWordRuns(fileBytes);
        if (foreignWordRuns.isEmpty()) {
            return null;
        }
        final List<Charset> order = candidateOrder(fileBytes, foreignWordRuns, wholeFileCandidates);
        Charset best = null;
        double bestScore = 0.0;
        for (final Charset charset : order) {
            final double score = coherenceScore(fileBytes, foreignWordRuns, charset);
            if (score > bestScore) {
                bestScore = score;
                best = charset;
            }
        }
        return bestScore > 0.0 ? best : null;
    }

    /** The single-byte candidates to score, region detection's own list first (in its order), then any whole-file candidate not already present. */
    private static List<Charset> candidateOrder(
            byte[] fileBytes, List<int[]> foreignWordRuns, List<CharsetLadder.Detection> wholeFileCandidates) {
        final List<CharsetLadder.Detection> regionCandidates =
                CharsetLadder.detectAll(concatenateRuns(fileBytes, foreignWordRuns));
        final List<Charset> order = new ArrayList<>();
        appendSingleByteCharsets(order, regionCandidates);
        appendSingleByteCharsets(order, wholeFileCandidates);
        return order;
    }

    private static void appendSingleByteCharsets(List<Charset> order, List<CharsetLadder.Detection> detections) {
        for (final CharsetLadder.Detection detection : detections) {
            final Charset charset = detection.charset();
            if (isSingleByte(charset) && !order.contains(charset)) {
                order.add(charset);
            }
        }
    }

    /** The foreign-word runs' bytes, each run separated from the next by a single space byte. */
    private static byte[] concatenateRuns(byte[] fileBytes, List<int[]> runs) {
        int length = runs.size() - 1;
        for (final int[] run : runs) {
            length += run[1] - run[0];
        }
        final byte[] region = new byte[length];
        int position = 0;
        for (int i = 0; i < runs.size(); i++) {
            final int[] run = runs.get(i);
            final int runLength = run[1] - run[0];
            System.arraycopy(fileBytes, run[0], region, position, runLength);
            position += runLength;
            if (i < runs.size() - 1) {
                region[position++] = ' ';
            }
        }
        return region;
    }

    private static boolean isSingleByte(Charset charset) {
        return charset.canEncode() && charset.newEncoder().maxBytesPerChar() == 1.0f;
    }

    /** The fraction of {@code runs} that {@link #isCoherentRun} accepts under {@code charset}. */
    private static double coherenceScore(byte[] fileBytes, List<int[]> runs, Charset charset) {
        long qualifying = 0;
        for (final int[] run : runs) {
            if (isCoherentRun(fileBytes, run, charset)) {
                qualifying++;
            }
        }
        return (double) qualifying / runs.size();
    }

    /** Whether {@code run}, decoded under {@code charset}, is all letters of one non-Latin {@link Character.UnicodeScript}. */
    private static boolean isCoherentRun(byte[] fileBytes, int[] run, Charset charset) {
        final String decoded = new String(fileBytes, run[0], run[1] - run[0], charset);
        Character.UnicodeScript runScript = null;
        for (int i = 0; i < decoded.length(); ) {
            final int codePoint = decoded.codePointAt(i);
            i += Character.charCount(codePoint);
            if (!Character.isLetter(codePoint)) {
                return false;
            }
            final Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
            if (script == Character.UnicodeScript.LATIN) {
                return false;
            }
            if (runScript == null) {
                runScript = script;
            } else if (runScript != script) {
                return false;
            }
        }
        return runScript != null;
    }

    /**
     * Every maximal run of two or more consecutive bytes {@code >= 0x80} in {@code fileBytes} whose immediate
     * neighbouring byte, where one exists, is not an ASCII letter.
     *
     * @param fileBytes the bytes to scan
     * @return each run as a {@code {start, end}} pair of byte offsets, {@code end} exclusive; never null,
     *     possibly empty
     */
    private static List<int[]> foreignWordRuns(byte[] fileBytes) {
        final List<int[]> runs = new ArrayList<>();
        int i = 0;
        while (i < fileBytes.length) {
            if (isHighByte(fileBytes[i])) {
                final int start = i;
                while (i < fileBytes.length && isHighByte(fileBytes[i])) {
                    i++;
                }
                if (i - start >= 2 && !bordersAsciiLetter(fileBytes, start, i)) {
                    runs.add(new int[] {start, i});
                }
            } else {
                i++;
            }
        }
        return runs;
    }

    private static boolean isHighByte(byte b) {
        return (b & 0xFF) >= 0x80;
    }

    private static boolean bordersAsciiLetter(byte[] fileBytes, int start, int end) {
        final boolean leftIsLetter = start > 0 && isAsciiLetter(fileBytes[start - 1]);
        final boolean rightIsLetter = end < fileBytes.length && isAsciiLetter(fileBytes[end]);
        return leftIsLetter || rightIsLetter;
    }

    private static boolean isAsciiLetter(byte b) {
        return (b >= 'A' && b <= 'Z') || (b >= 'a' && b <= 'z');
    }
}

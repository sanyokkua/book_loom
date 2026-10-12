package ua.bookloom.pipeline.setup;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.pipeline.setup.Knowledge.Reason;

/**
 * Judges what the model said it knows of the book against the book's own metadata. The answer stands only when the
 * model says it knows the book, is sure enough, and echoes the metadata's title and author and a year near the
 * metadata's: the echo is what shows the answer is about this book, since a model asked about an invented book can
 * still answer with confidence. The first check that fails names the reason.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BookKnowledgeVerdict {

    /** An answer the model is less sure of than this is a guess. */
    static final double MIN_CONFIDENCE = 0.7;

    /** The most years the answer's year may differ from the metadata's. */
    static final int YEAR_TOLERANCE = 5;

    // The edit distance may be a fifth of the longer text; compared as distance * 5 <= length, in integers.
    private static final int EDIT_SHARE_DIVISOR = 5;
    private static final Set<String> ARTICLES = Set.of("the", "a", "an");
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NOT_ALPHANUMERIC = Pattern.compile("[^\\p{L}\\p{N}]+");

    /**
     * Why no question is asked about this book, so no answer can stand.
     *
     * @param facts the book's metadata
     * @param fileStem the file name without its extension
     * @return {@link Reason#NO_TITLE} when the book has no title, {@link Reason#TITLE_IS_FILE_STEM} when the title is
     *     only the file's name, else null
     */
    static @Nullable Reason skipReason(final BookFacts facts, final String fileStem) {
        Objects.requireNonNull(facts, "facts");
        Objects.requireNonNull(fileStem, "fileStem");
        final String title = facts.title() == null ? "" : titleKey(facts.title());
        if (title.isEmpty()) {
            return Reason.NO_TITLE;
        }
        return title.equals(titleKey(fileStem)) ? Reason.TITLE_IS_FILE_STEM : null;
    }

    /**
     * Judges one answer.
     *
     * @param facts the book's metadata
     * @param fileStem the file name without its extension
     * @param reply the model's answer, or null when it could not be read
     * @return the answer when it stands, else unknown with the first reason it does not; never null
     */
    public static Knowledge judge(
            final BookFacts facts, final String fileStem, final @Nullable BookKnowledgeReply reply) {
        final Reason skipped = skipReason(facts, fileStem);
        final Reason reason = skipped != null ? skipped : refusal(facts, reply);
        log.debug("Knowledge verdict: reason={}", reason == null ? "none" : reason.label());
        return reason == null ? Knowledge.known(Objects.requireNonNull(reply, "reply")) : Knowledge.unknown(reason);
    }

    private static @Nullable Reason refusal(final BookFacts facts, final @Nullable BookKnowledgeReply reply) {
        if (reply == null) {
            return Reason.UNPARSABLE;
        }
        if (!reply.known()) {
            return Reason.NOT_KNOWN;
        }
        if (reply.confidence() < MIN_CONFIDENCE) {
            log.debug("Knowledge confidence {} is under {}", reply.confidence(), MIN_CONFIDENCE);
            return Reason.LOW_CONFIDENCE;
        }
        if (!echoes(titleKey(facts.title()), titleKey(reply.title()))) {
            return Reason.TITLE_MISMATCH;
        }
        if (facts.author() != null && !echoes(key(facts.author()), key(reply.author()))) {
            return Reason.AUTHOR_MISMATCH;
        }
        return yearAgrees(facts.year(), reply.year()) ? null : Reason.YEAR_MISMATCH;
    }

    private static boolean yearAgrees(final @Nullable Integer metadata, final @Nullable Integer answered) {
        if (metadata == null) {
            return true;
        }
        return answered != null && Math.abs(metadata - answered) <= YEAR_TOLERANCE;
    }

    private static boolean echoes(final String metadata, final String echoed) {
        final int distance = editDistance(metadata, echoed);
        final int longer = Math.max(metadata.length(), echoed.length());
        final boolean matches = distance * EDIT_SHARE_DIVISOR <= longer;
        log.trace(
                "Echo check: metadata='{}' echoed='{}' distance={} longer={} matches={}",
                metadata,
                echoed,
                distance,
                longer,
                matches);
        return matches;
    }

    /** Lower case, no diacritics, every run of other characters one space. */
    private static String key(final @Nullable String text) {
        if (text == null) {
            return "";
        }
        final String bare =
                MARKS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("");
        return NOT_ALPHANUMERIC
                .matcher(bare.toLowerCase(Locale.ROOT))
                .replaceAll(" ")
                .strip();
    }

    /** A title's key without a leading article, which a catalogue may leave out or put in. */
    private static String titleKey(final @Nullable String title) {
        final String key = key(title);
        final int space = key.indexOf(' ');
        return space > 0 && ARTICLES.contains(key.substring(0, space)) ? key.substring(space + 1) : key;
    }

    private static int editDistance(final String left, final String right) {
        int[] previous = new int[right.length() + 1];
        for (int column = 0; column <= right.length(); column++) {
            previous[column] = column;
        }
        for (int row = 1; row <= left.length(); row++) {
            final int[] current = new int[right.length() + 1];
            current[0] = row;
            for (int column = 1; column <= right.length(); column++) {
                final int substitution = left.charAt(row - 1) == right.charAt(column - 1) ? 0 : 1;
                current[column] = Math.min(
                        Math.min(current[column - 1] + 1, previous[column] + 1), previous[column - 1] + substitution);
            }
            previous = current;
        }
        return previous[right.length()];
    }
}

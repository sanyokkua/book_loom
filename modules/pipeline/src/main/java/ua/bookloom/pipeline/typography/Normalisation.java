package ua.bookloom.pipeline.typography;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The typography pass's answer for one text.
 *
 * @param text the normalised text; the input itself when nothing needed fixing
 * @param apostrophes how many straight apostrophes inside words became typographic
 * @param ellipses how many three-dot ellipses became one character
 * @param quotes how many straight quote marks became the language's marks
 * @param spaces how many spaces before a punctuation mark were removed
 * @param speech the source-aware speech rewrites that fired, in the order they ran; each kind once with its count
 * @param repairs how many times each code repair of the 10.3 tally fired
 */
public record Normalisation(
        String text, int apostrophes, int ellipses, int quotes, int spaces, List<Tally> speech, Repairs repairs) {

    /**
     * The code repairs that run before any check, each with its count; they have no check level because the defect is
     * gone before a check can see it.
     *
     * @param stress stress marks removed
     * @param leadingDash lost leading dialogue dashes put back
     * @param dialogueDash dashes rewritten to the one form
     * @param leadingNumber lost heading numbers put back
     * @param isDashLostBeforeQuote whether the target lost the source's leading dash but starts with a quote mark, which
     *     is only noted
     */
    public record Repairs(
            int stress, int leadingDash, int dialogueDash, int leadingNumber, boolean isDashLostBeforeQuote) {

        /** No repair fired. */
        public static final Repairs NONE = new Repairs(0, 0, 0, 0, false);

        boolean isAny() {
            return stress + leadingDash + dialogueDash + leadingNumber > 0 || isDashLostBeforeQuote;
        }
    }

    /**
     * One kind of speech rewrite and how often it fired.
     *
     * @param label the words that name the rewrite in the note, printed after the count
     * @param count how many times it fired; positive
     */
    public record Tally(String label, int count) {}

    /** Rejects a missing text or tally list. */
    public Normalisation {
        Objects.requireNonNull(text, "text");
        speech = List.copyOf(speech);
        Objects.requireNonNull(repairs, "repairs");
    }

    /**
     * Whether a code repair of the given kind fired.
     *
     * @param key {@code stress-mark}, {@code leading-dash}, {@code dialogue-dash} or {@code heading-number}
     * @return {@code true} if that repair changed the text at least once, {@code false} otherwise
     */
    public boolean fired(final String key) {
        return switch (key) {
            case "stress-mark" -> repairs.stress() > 0;
            case "leading-dash" -> repairs.leadingDash() > 0;
            case "dialogue-dash" -> repairs.dialogueDash() > 0;
            case "heading-number" -> repairs.leadingNumber() > 0;
            default -> false;
        };
    }

    /**
     * Whether the pass changed anything.
     *
     * @return {@code true} when at least one mark or space was changed
     */
    public boolean isChanged() {
        return apostrophes + ellipses + quotes + spaces > 0 || !speech.isEmpty() || repairs.isAny();
    }

    /**
     * The one-line note a review shows beside a normalised segment.
     *
     * @return the note naming each kind of change with its count; empty when nothing changed
     */
    public String note() {
        if (!isChanged()) {
            return "";
        }
        final List<String> parts = new ArrayList<>();
        add(parts, apostrophes, "apostrophe", "apostrophes");
        add(parts, ellipses, "ellipsis", "ellipses");
        add(parts, quotes, "quote mark", "quote marks");
        add(parts, spaces, "space", "spaces");
        speech.forEach(tally -> parts.add(tally.count() + " " + tally.label()));
        add(parts, repairs.stress(), "stress mark removed", "stress marks removed");
        add(parts, repairs.leadingDash(), "lost leading dash put back", "lost leading dashes put back");
        add(parts, repairs.dialogueDash(), "dash written in one form", "dashes written in one form");
        add(parts, repairs.leadingNumber(), "lost heading number put back", "lost heading numbers put back");
        if (repairs.isDashLostBeforeQuote()) {
            parts.add("the source's leading dash is missing before a quote mark");
        }
        return "Typography normalised: " + String.join(", ", parts) + ".";
    }

    private static void add(final List<String> parts, final int count, final String one, final String many) {
        if (count > 0) {
            parts.add(count + " " + (count == 1 ? one : many));
        }
    }
}

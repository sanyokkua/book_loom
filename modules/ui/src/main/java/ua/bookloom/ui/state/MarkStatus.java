package ua.bookloom.ui.state;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which of a segment's formatting marks the translation editor lacks or holds twice, and the two spellings of a mark:
 * {@code ⟦gN⟧} as stored and {@code ⟦N⟧} as the person reads and edits it. Only the number form counts as a mark here;
 * a hand-typed {@code ⟦g1⟧} is plain text, which the pipeline's own net refuses on save.
 *
 * @param missing each mark of the source the editor lacks, once per missing occurrence, in the source's order
 * @param doubled each mark the editor holds more often than the source, once, in the editor's order
 */
public record MarkStatus(List<String> missing, List<String> doubled) {

    private static final Pattern MARK = Pattern.compile("⟦\\d+⟧");
    private static final Pattern STORED = Pattern.compile("⟦g(\\d+)⟧");
    private static final Pattern SHOWN = Pattern.compile("⟦(\\d+)⟧");

    /** Copies both lists so the record stays immutable. */
    public MarkStatus {
        missing = List.copyOf(Objects.requireNonNull(missing, "missing"));
        doubled = List.copyOf(Objects.requireNonNull(doubled, "doubled"));
    }

    /**
     * Whether every mark of the source is in the editor exactly once (or as often as the source holds it).
     *
     * @return {@code true} if nothing is missing and nothing is doubled
     */
    public boolean isComplete() {
        return missing.isEmpty() && doubled.isEmpty();
    }

    /**
     * Compares the editor with the source, both in the number form.
     *
     * @param sourceDisplay the non-null source text
     * @param editorDisplay the non-null text in the editor
     * @return the marks missing from, and doubled in, the editor; a mark the source lacks is never counted
     */
    public static MarkStatus of(final String sourceDisplay, final String editorDisplay) {
        Objects.requireNonNull(sourceDisplay, "sourceDisplay");
        Objects.requireNonNull(editorDisplay, "editorDisplay");
        final List<String> wanted = marksOf(sourceDisplay);
        final Map<String, Integer> have = counts(marksOf(editorDisplay));
        final Map<String, Integer> want = counts(wanted);
        final List<String> missing = new ArrayList<>();
        final Map<String, Integer> remaining = new HashMap<>(have);
        for (final String mark : wanted) {
            if (remaining.merge(mark, -1, Integer::sum) < 0) {
                missing.add(mark);
            }
        }
        final List<String> doubled = marksOf(editorDisplay).stream()
                .distinct()
                .filter(mark -> want.containsKey(mark) && have.getOrDefault(mark, 0) > want.getOrDefault(mark, 0))
                .toList();
        return new MarkStatus(missing, doubled);
    }

    /**
     * The marks of a text in the number form.
     *
     * @param text the non-null text
     * @return every {@code ⟦N⟧} in order, repeats included; empty if none
     */
    public static List<String> marksOf(final String text) {
        Objects.requireNonNull(text, "text");
        final List<String> marks = new ArrayList<>();
        final Matcher matcher = MARK.matcher(text);
        while (matcher.find()) {
            marks.add(matcher.group());
        }
        return marks;
    }

    /**
     * Turns the stored marks into the number form the person reads.
     *
     * @param masked the non-null masked text holding {@code ⟦gN⟧}
     * @return the same text with {@code ⟦N⟧}
     */
    public static String show(final String masked) {
        return STORED.matcher(Objects.requireNonNull(masked, "masked")).replaceAll("⟦$1⟧");
    }

    /**
     * Turns the number form back into the stored marks.
     *
     * @param display the non-null editor text holding {@code ⟦N⟧}
     * @return the same text with {@code ⟦gN⟧}
     */
    public static String store(final String display) {
        return SHOWN.matcher(Objects.requireNonNull(display, "display")).replaceAll("⟦g$1⟧");
    }

    private static Map<String, Integer> counts(final List<String> marks) {
        final Map<String, Integer> counts = new HashMap<>();
        marks.forEach(mark -> counts.merge(mark, 1, Integer::sum));
        return counts;
    }
}

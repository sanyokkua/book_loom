package ua.bookloom.ui.control;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TextInputControl;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.ui.state.MarkStatus;

/**
 * The translation editor's filter that keeps each {@code ⟦N⟧} formatting mark whole: a delete or replace that touches
 * part of a mark takes all of it, typing inside a mark lands after it, and a pasted mark the text already holds (or the
 * source lacks) is dropped. A whole-text replacement ({@code setText}, which is how a segment is loaded) passes
 * unchanged, and undo and redo skip the filter on JavaFX 26, so neither is touched. Nothing is logged: it runs on every
 * keystroke.
 */
@Slf4j
public final class ProtectedMarks implements UnaryOperator<TextFormatter.Change> {

    private static final Pattern MARK = Pattern.compile("⟦\\d+⟧");

    private final Supplier<String> sourceDisplay;
    private final boolean keepsEveryMark;

    private ProtectedMarks(final Supplier<String> sourceDisplay, final boolean keepsEveryMark) {
        this.sourceDisplay = sourceDisplay;
        this.keepsEveryMark = keepsEveryMark;
    }

    /**
     * Protects the marks of {@code area}.
     *
     * @param area the non-null translation editor
     * @param sourceDisplay the non-null supplier of the segment's source in the number form, read at each paste
     */
    public static void install(final TextArea area, final Supplier<String> sourceDisplay) {
        Objects.requireNonNull(area, "area");
        Objects.requireNonNull(sourceDisplay, "sourceDisplay");
        log.debug("protecting the formatting marks of the editor {}", area.getId());
        area.setTextFormatter(new TextFormatter<String>(new ProtectedMarks(sourceDisplay, false)));
    }

    /**
     * Protects the marks of a one-line field, such as the book's title, where no mark may be deleted at all: a delete
     * or replace keeps every whole mark it covers, so the text can never lack one.
     *
     * @param field the non-null field
     * @param sourceDisplay the non-null supplier of the original in the number form, read at each paste
     */
    public static void install(final TextInputControl field, final Supplier<String> sourceDisplay) {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(sourceDisplay, "sourceDisplay");
        log.debug("protecting every formatting mark of the field {}", field.getId());
        field.setTextFormatter(new TextFormatter<String>(new ProtectedMarks(sourceDisplay, true)));
    }

    @Override
    public TextFormatter.Change apply(final TextFormatter.Change change) {
        final String old = change.getControlText();
        if (!change.isContentChange() || (change.getRangeStart() == 0 && change.getRangeEnd() == old.length())) {
            return change;
        }
        int start = change.getRangeStart();
        int end = change.getRangeEnd();
        if (start == end) {
            start = relocated(old, start);
            end = start;
        } else {
            start = widenedStart(old, start);
            end = relocated(old, end);
        }
        final String covered = keepsEveryMark ? String.join("", MarkStatus.marksOf(old.substring(start, end))) : "";
        final String kept = covered + dedupe(old.substring(0, start) + covered + old.substring(end), change.getText());
        change.setRange(start, end);
        change.setText(kept);
        final int caret = start + kept.length();
        change.selectRange(caret, caret);
        return change;
    }

    // The end of the mark a position falls inside, else the position itself.
    private static int relocated(final String text, final int at) {
        final Matcher matcher = MARK.matcher(text);
        while (matcher.find()) {
            if (matcher.start() < at && at < matcher.end()) {
                return matcher.end();
            }
        }
        return at;
    }

    private static int widenedStart(final String text, final int at) {
        final Matcher matcher = MARK.matcher(text);
        while (matcher.find()) {
            if (matcher.start() < at && at < matcher.end()) {
                return matcher.start();
            }
        }
        return at;
    }

    // A mark the text still holds as often as the source does is dropped from the paste, with one space after it.
    private String dedupe(final String remaining, final String inserted) {
        final Map<String, Integer> room = new HashMap<>();
        final Map<String, Integer> have = new HashMap<>();
        MarkStatus.marksOf(sourceDisplay.get()).forEach(mark -> room.merge(mark, 1, Integer::sum));
        MarkStatus.marksOf(remaining).forEach(mark -> have.merge(mark, 1, Integer::sum));
        final StringBuilder out = new StringBuilder();
        final Matcher matcher = MARK.matcher(inserted);
        int from = 0;
        while (matcher.find()) {
            out.append(inserted, from, matcher.start());
            from = matcher.end();
            if (room.getOrDefault(matcher.group(), 0) > have.getOrDefault(matcher.group(), 0)) {
                have.merge(matcher.group(), 1, Integer::sum);
                out.append(matcher.group());
            } else if (from < inserted.length() && inserted.charAt(from) == ' ') {
                from++;
            }
        }
        return out.append(inserted, from, inserted.length()).toString();
    }
}

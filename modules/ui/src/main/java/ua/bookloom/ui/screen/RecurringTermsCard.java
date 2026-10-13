package ua.bookloom.ui.screen;

import java.text.Collator;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ChangeMarks;
import ua.bookloom.ui.state.NamesStyleViewModel;
import ua.bookloom.ui.state.RecurringTerms;

/**
 * The rows of the Recurring terms card: common words and titles the book repeats, with the target later requests are
 * asked to keep, and the actions to move a term to the glossary or remove it. The card around them is the
 * {@link TermTableCard} the Glossary card is built from too; its Scan, Review and Translate are the screen's model
 * actions, offered and blocked as the Glossary's are.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class RecurringTermsCard {

    private static final double TERM_WIDTH = 170;
    private static final double TARGET_WIDTH = 260;
    private static final double PROMOTE_WIDTH = 120;
    private static final double REMOVE_WIDTH = 40;

    private static final Comparator<LexiconEntry> BY_TERM =
            Comparator.comparing(LexiconEntry::term, Collator.getInstance());

    static TermTableCard.Rows<LexiconEntry> rows(final Messages messages, final NamesStyleViewModel names) {
        final ChangeMarks.Side marks = names.marks().terms();
        return new TermTableCard.Rows<>(
                names.recurring().rows(),
                marks,
                entry -> LexiconEntry.keyOf(entry.term()),
                entry -> Arrays.asList(entry.term(), entry.established().orElse(null)),
                BY_TERM,
                columns(messages, names.recurring(), marks),
                List.of());
    }

    private static List<TableColumn<LexiconEntry, LexiconEntry>> columns(
            final Messages messages, final RecurringTerms recurring, final ChangeMarks.Side marks) {
        return List.of(
                column(
                        messages,
                        MessageKey.RECURRING_COLUMN_TERM,
                        true,
                        MessageKey.RECURRING_COLUMN_TERM_TIP,
                        TERM_WIDTH,
                        false,
                        () -> new RecurringTermsCells.TermCell(messages, marks)),
                targetColumn(messages, recurring),
                actionColumn(
                        messages,
                        MessageKey.RECURRING_PROMOTE,
                        MessageKey.RECURRING_PROMOTE_TIP,
                        PROMOTE_WIDTH,
                        () -> new RecurringTermsCells.PromoteCell(messages, recurring)),
                actionColumn(
                        messages,
                        MessageKey.NAMES_STYLE_COLUMN_REMOVE,
                        MessageKey.NAMES_STYLE_COLUMN_REMOVE_TIP,
                        REMOVE_WIDTH,
                        () -> new RecurringTermsCells.RemoveCell(messages, recurring)));
    }

    private static TableColumn<LexiconEntry, LexiconEntry> targetColumn(
            final Messages messages, final RecurringTerms recurring) {
        return column(
                messages,
                MessageKey.RECURRING_COLUMN_TARGET,
                true,
                MessageKey.RECURRING_COLUMN_TARGET_TIP,
                TARGET_WIDTH,
                false,
                () -> new RecurringTermsCells.RenderingCell(messages, recurring));
    }

    // An action column has no header text; it explains itself on hover.
    private static TableColumn<LexiconEntry, LexiconEntry> actionColumn(
            final Messages messages,
            final MessageKey header,
            final MessageKey tip,
            final double width,
            final Supplier<TableCell<LexiconEntry, LexiconEntry>> cell) {
        return column(messages, header, false, tip, width, true, cell);
    }

    private static TableColumn<LexiconEntry, LexiconEntry> column(
            final Messages messages,
            final MessageKey header,
            final boolean shown,
            final MessageKey tip,
            final double width,
            final boolean fixed,
            final Supplier<TableCell<LexiconEntry, LexiconEntry>> cell) {
        return TermTableCard.column(messages, header, shown, tip, width, fixed, null, cell);
    }
}

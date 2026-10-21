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
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GenderOrigin;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.ui.i18n.GlossaryLabels;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.NamesStyleViewModel;

/**
 * The rows of the Glossary card: Term, Type, Target, Gender, Locked and a last column holding the remove cross, each
 * edited in its own cell. The card around them is the {@link TermTableCard} the Recurring terms card is built from too.
 *
 * <p>Term, Type, Gender and Locked sort by a header click. Ties are always broken by the term, which no two rows share,
 * so a change to anything but the sorted column — above all a target being typed — never moves its row; a change to
 * the sorted column moves the row once the change is stored.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class GlossaryTable {

    private static final double TERM_WIDTH = 170;
    private static final double TERM_MIN = 140;
    private static final double TYPE_WIDTH = 110;
    private static final double TYPE_MIN = 96;
    private static final double TARGET_WIDTH = 220;
    private static final double TARGET_MIN = 170;
    private static final double GENDER_WIDTH = 150;
    private static final double GENDER_MIN = 110;
    private static final double LOCK_WIDTH = 90;
    private static final double LOCK_MIN = 64;
    private static final double REMOVE_WIDTH = 40;
    private static final double REMOVE_MIN = 36;
    private static final String NOT_A_CHARACTER = "—";

    private static final Comparator<GlossaryEntry> BY_TERM =
            Comparator.comparing(GlossaryEntry::term, Collator.getInstance());

    static TermTableCard.Rows<GlossaryEntry> rows(final Messages messages, final NamesStyleViewModel model) {
        return new TermTableCard.Rows<>(
                model.rows(),
                model.marks().glossary(),
                GlossaryEntry::id,
                entry -> Arrays.asList(entry.term(), entry.target()),
                BY_TERM,
                columns(messages, model),
                List.of(model.restorations()));
    }

    private static List<TableColumn<GlossaryEntry, GlossaryEntry>> columns(
            final Messages messages, final NamesStyleViewModel model) {
        return List.of(
                column(
                        messages,
                        MessageKey.NAMES_STYLE_COLUMN_SOURCE,
                        true,
                        MessageKey.NAMES_STYLE_COLUMN_SOURCE_TIP,
                        TERM_WIDTH,
                        TERM_MIN,
                        BY_TERM,
                        () -> new GlossaryCells.TermCell()),
                column(
                        messages,
                        MessageKey.NAMES_STYLE_COLUMN_TYPE,
                        true,
                        MessageKey.NAMES_STYLE_COLUMN_TYPE_TIP,
                        TYPE_WIDTH,
                        TYPE_MIN,
                        Comparator.comparing(GlossaryEntry::type),
                        () -> typeCell(messages, model)),
                targetColumn(messages, model),
                genderColumn(messages, model),
                lockColumn(messages, model),
                removeColumn(messages, model));
    }

    private static TableColumn<GlossaryEntry, GlossaryEntry> targetColumn(
            final Messages messages, final NamesStyleViewModel model) {
        return column(
                messages,
                MessageKey.NAMES_STYLE_COLUMN_TARGET,
                true,
                MessageKey.NAMES_STYLE_COLUMN_TARGET_TIP,
                TARGET_WIDTH,
                TARGET_MIN,
                null,
                () -> new GlossaryCells.TargetCell(messages, model));
    }

    private static TableColumn<GlossaryEntry, GlossaryEntry> genderColumn(
            final Messages messages, final NamesStyleViewModel model) {
        return column(
                messages,
                MessageKey.NAMES_STYLE_COLUMN_GENDER,
                true,
                MessageKey.NAMES_STYLE_COLUMN_GENDER_TIP,
                GENDER_WIDTH,
                GENDER_MIN,
                Comparator.comparing(GlossaryEntry::gender),
                () -> genderCell(messages, model));
    }

    private static TableColumn<GlossaryEntry, GlossaryEntry> lockColumn(
            final Messages messages, final NamesStyleViewModel model) {
        return column(
                messages,
                MessageKey.NAMES_STYLE_COLUMN_LOCKED,
                true,
                MessageKey.NAMES_STYLE_COLUMN_LOCKED_TIP,
                LOCK_WIDTH,
                LOCK_MIN,
                Comparator.comparing(GlossaryEntry::locked),
                () -> new GlossaryCells.LockCell(messages, model));
    }

    private static TableColumn<GlossaryEntry, GlossaryEntry> removeColumn(
            final Messages messages, final NamesStyleViewModel model) {
        return column(
                messages,
                MessageKey.NAMES_STYLE_COLUMN_REMOVE,
                false,
                MessageKey.NAMES_STYLE_COLUMN_REMOVE_TIP,
                REMOVE_WIDTH,
                REMOVE_MIN,
                null,
                () -> new GlossaryCells.RemoveCell(messages, model));
    }

    private static TableColumn<GlossaryEntry, GlossaryEntry> column(
            final Messages messages,
            final MessageKey header,
            final boolean shown,
            final MessageKey tip,
            final double width,
            final double minWidth,
            final @Nullable Comparator<GlossaryEntry> order,
            final Supplier<TableCell<GlossaryEntry, GlossaryEntry>> cell) {
        return TermTableCard.column(messages, header, shown, tip, width, minWidth, order, cell);
    }

    private static GlossaryCells.EntryCell<?> typeCell(final Messages messages, final NamesStyleViewModel model) {
        return new GlossaryChoiceCell<>(
                messages,
                MessageKey.NAMES_STYLE_COLUMN_TYPE_TIP,
                TermType.values(),
                type -> GlossaryLabels.type(messages, type),
                GlossaryEntry::type,
                entry -> GlossaryLabels.type(messages, entry.type()),
                model::setType);
    }

    private static GlossaryCells.EntryCell<?> genderCell(final Messages messages, final NamesStyleViewModel model) {
        return new GlossaryChoiceCell<>(
                messages,
                MessageKey.NAMES_STYLE_COLUMN_GENDER_TIP,
                Gender.values(),
                gender -> GlossaryLabels.gender(messages, gender),
                GlossaryEntry::gender,
                entry -> genderLabel(messages, entry),
                model::setGender);
    }

    // Only a character has a gender to settle; a place or a term shows a dash instead of "unknown". A character the
    // scan read and found no evidence for says so (15i.N5), which a plain "unknown" would not.
    private static String genderLabel(final Messages messages, final GlossaryEntry entry) {
        if (entry.type() != TermType.CHARACTER) {
            return NOT_A_CHARACTER;
        }
        if (entry.noGenderEvidence()) {
            return messages.get(MessageKey.NAMES_STYLE_GENDER_NO_EVIDENCE);
        }
        final String label = GlossaryLabels.gender(messages, entry.gender());
        return entry.genderOrigin() == GenderOrigin.SUGGESTED
                ? messages.get(MessageKey.NAMES_STYLE_GENDER_SUGGESTED, label)
                : label;
    }
}

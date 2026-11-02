package ua.bookloom.ui.screen;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import javafx.beans.value.ObservableBooleanValue;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.NameKnowledge;
import ua.bookloom.api.project.Origin;
import ua.bookloom.api.project.TargetState;
import ua.bookloom.api.project.TermType;
import ua.bookloom.ui.i18n.GlossaryLabels;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.NamesStyleViewModel;

/**
 * The rows of the Glossary card: Term, Kind, Forms, Translation, State, Gender and a last column holding the remove
 * cross, each edited in its own cell. Kind, Translation and Gender say where their value came from (person, book, rule
 * or likely). The card around them is the {@link TermTableCard} the Recurring terms card is built from too.
 *
 * <p>Term, Kind, State and Gender sort by a header click. Ties are always broken by the term, which no two rows share,
 * so a change to anything but the sorted column — above all a target being typed — never moves its row; a change to
 * the sorted column moves the row once the change is stored. The search reads the term, every form and the target.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class GlossaryTable {

    // Each minimum keeps its header and a typical value whole; each column starts about a third wider.
    private static final double TERM_MIN = 140;
    private static final double KIND_MIN = 150;
    private static final double FORMS_MIN = 140;
    private static final double TARGET_MIN = 170;
    private static final double STATE_MIN = 96;
    private static final double GENDER_MIN = 130;
    private static final double REMOVE_MIN = 36;
    private static final double START_SHARE = 1.3;
    // A value with its origin ("female · book") wraps under itself instead of ending in an ellipsis.
    private static final String WRAPPING_CHOICE = "glossary-wraps";
    private static final double REMOVE_WIDTH = 40;
    private static final String NOT_A_CHARACTER = "—";
    private static final List<TargetState> STATE_ORDER =
            List.of(TargetState.SUGGESTED, TargetState.PINNED, TargetState.LOCKED);
    private static final TargetState[] CHOSEN_STATES = {TargetState.PINNED, TargetState.LOCKED};

    private static final Comparator<GlossaryEntry> BY_TERM =
            Comparator.comparing(GlossaryEntry::term, Collator.getInstance());

    private static final Heading TERM = new Heading(
            MessageKey.NAMES_STYLE_COLUMN_SOURCE, MessageKey.NAMES_STYLE_COLUMN_SOURCE_TIP, TERM_MIN, BY_TERM);
    private static final Heading KIND = new Heading(
            MessageKey.NAMES_STYLE_COLUMN_TYPE,
            MessageKey.NAMES_STYLE_COLUMN_TYPE_TIP,
            KIND_MIN,
            Comparator.comparing(GlossaryEntry::type));
    private static final Heading FORMS =
            new Heading(MessageKey.NAMES_STYLE_COLUMN_FORMS, MessageKey.NAMES_STYLE_COLUMN_FORMS_TIP, FORMS_MIN, null);
    private static final Heading TARGET = new Heading(
            MessageKey.NAMES_STYLE_COLUMN_TARGET, MessageKey.NAMES_STYLE_COLUMN_TARGET_TIP, TARGET_MIN, null);
    private static final Heading STATE = new Heading(
            MessageKey.NAMES_STYLE_COLUMN_STATE,
            MessageKey.NAMES_STYLE_COLUMN_STATE_TIP,
            STATE_MIN,
            Comparator.comparingInt(GlossaryTable::stateRank));
    private static final Heading GENDER = new Heading(
            MessageKey.NAMES_STYLE_COLUMN_GENDER,
            MessageKey.NAMES_STYLE_COLUMN_GENDER_TIP,
            GENDER_MIN,
            Comparator.comparing(GlossaryEntry::gender));

    static TermTableCard.Rows<GlossaryEntry> rows(
            final Messages messages, final NamesStyleViewModel model, final ObservableBooleanValue frozen) {
        return new TermTableCard.Rows<>(
                model.rows(),
                model.marks().glossary(),
                GlossaryEntry::id,
                GlossaryTable::searchable,
                BY_TERM,
                columns(messages, model, frozen),
                List.of(model.restorations(), model.reviewFlagRefreshes()));
    }

    // A name is found by any spelling the book uses, or by its translation.
    private static List<@Nullable String> searchable(final GlossaryEntry entry) {
        final List<@Nullable String> texts = new ArrayList<>(entry.names());
        texts.add(entry.target());
        return texts;
    }

    private static List<TableColumn<GlossaryEntry, GlossaryEntry>> columns(
            final Messages messages, final NamesStyleViewModel model, final ObservableBooleanValue frozen) {
        return List.of(
                column(messages, TERM, () -> new GlossaryCells.TermCell(messages)),
                column(messages, KIND, () -> typeCell(messages, model, frozen)),
                column(messages, FORMS, () -> new GlossaryFormsCell(messages)),
                column(messages, TARGET, () -> new GlossaryCells.TargetCell(messages, model, frozen)),
                column(messages, STATE, () -> stateCell(messages, model, frozen)),
                column(messages, GENDER, () -> genderCell(messages, model, frozen)),
                TermTableCard.column(
                        messages,
                        new TermTableCard.Head(
                                MessageKey.NAMES_STYLE_COLUMN_REMOVE, false, MessageKey.NAMES_STYLE_COLUMN_REMOVE_TIP),
                        REMOVE_WIDTH,
                        REMOVE_MIN,
                        null,
                        () -> new GlossaryCells.RemoveCell(messages, model, frozen)));
    }

    // A shown column starts about a third wider than its minimum.
    private static TableColumn<GlossaryEntry, GlossaryEntry> column(
            final Messages messages,
            final Heading heading,
            final Supplier<TableCell<GlossaryEntry, GlossaryEntry>> cell) {
        return TermTableCard.column(
                messages,
                new TermTableCard.Head(heading.header(), true, heading.tip()),
                Math.rint(heading.minWidth() * START_SHARE),
                heading.minWidth(),
                heading.order(),
                cell);
    }

    /**
     * A shown column's words, minimum width and order.
     *
     * @param header the header's words
     * @param tip the header's hover explanation
     * @param minWidth the width the column cannot be dragged below
     * @param order the sort order of a header click, or null for a column that does not sort
     */
    private record Heading(
            MessageKey header,
            MessageKey tip,
            double minWidth,
            @Nullable Comparator<GlossaryEntry> order) {}

    // A row with no target has no state to show and sorts first; then suggested, pinned, locked.
    private static int stateRank(final GlossaryEntry entry) {
        return hasTarget(entry) ? STATE_ORDER.indexOf(entry.targetState()) : -1;
    }

    private static boolean hasTarget(final GlossaryEntry entry) {
        return entry.target() != null && !entry.target().isBlank();
    }

    // The Kind column holds "character · person" on one line; a narrower window or a longer word wraps the origin to a
    // second line of the row rather than ending in an ellipsis.
    private static GlossaryCells.EntryCell<?> typeCell(
            final Messages messages, final NamesStyleViewModel model, final ObservableBooleanValue frozen) {
        final GlossaryChoiceCell<TermType> cell = new GlossaryChoiceCell<>(
                messages,
                MessageKey.NAMES_STYLE_COLUMN_TYPE_TIP,
                TermType.values(),
                new GlossaryChoiceCell.Choice<>(
                        type -> GlossaryLabels.type(messages, type),
                        GlossaryEntry::type,
                        entry -> typeLabel(messages, entry),
                        model.rowChanges()::setType),
                frozen);
        cell.control().getStyleClass().add(WRAPPING_CHOICE);
        return cell;
    }

    // "other" is what a scan proposes before anything is known, so it carries no origin.
    private static String typeLabel(final Messages messages, final GlossaryEntry entry) {
        final String type = GlossaryLabels.type(messages, entry.type());
        return entry.type() == TermType.OTHER ? type : withOrigin(messages, type, entry.typeOrigin());
    }

    // The State of a row with no target is empty: choosing one is refused, since such a term needs a target.
    private static GlossaryCells.EntryCell<?> stateCell(
            final Messages messages, final NamesStyleViewModel model, final ObservableBooleanValue frozen) {
        return new GlossaryChoiceCell<>(
                messages,
                MessageKey.NAMES_STYLE_COLUMN_STATE_TIP,
                CHOSEN_STATES,
                new GlossaryChoiceCell.Choice<>(
                        state -> GlossaryLabels.state(messages, state),
                        GlossaryEntry::targetState,
                        entry -> hasTarget(entry) ? GlossaryLabels.state(messages, entry.targetState()) : "",
                        model.rowChanges()::setState),
                frozen);
    }

    private static GlossaryCells.EntryCell<?> genderCell(
            final Messages messages, final NamesStyleViewModel model, final ObservableBooleanValue frozen) {
        final GlossaryChoiceCell<Gender> cell = new GlossaryChoiceCell<>(
                messages,
                MessageKey.NAMES_STYLE_COLUMN_GENDER_TIP,
                Gender.values(),
                new GlossaryChoiceCell.Choice<>(
                        gender -> GlossaryLabels.gender(messages, gender),
                        GlossaryEntry::gender,
                        entry -> genderLabel(messages, entry),
                        model.rowChanges()::setGender),
                frozen);
        cell.control().getStyleClass().add(WRAPPING_CHOICE);
        return cell;
    }

    // Only a character has a gender to settle; a place or a term shows a dash. The column never reads "unknown": a
    // character with no gender yet reads "not specified", and adds that the book gave none only when the names pass
    // read
    // it and found none; one nobody has read, or the person cleared, says no more. A known gender says where it came
    // from, a likely one the model gave with low confidence says so, and a rule's gender the model disagrees with shows
    // both.
    private static String genderLabel(final Messages messages, final GlossaryEntry entry) {
        if (entry.type() != TermType.CHARACTER) {
            return NOT_A_CHARACTER;
        }
        if (entry.noGenderEvidence()) {
            return messages.get(MessageKey.NAMES_STYLE_GENDER_NO_EVIDENCE);
        }
        if (entry.gender() == Gender.UNKNOWN) {
            return messages.get(MessageKey.NAMES_STYLE_GENDER_NOT_SPECIFIED);
        }
        final String label = GlossaryLabels.gender(messages, entry.gender());
        final Origin origin = entry.genderOrigin();
        final NameKnowledge knowledge = entry.knowledge();
        if (origin == null) {
            return label;
        }
        if (knowledge != null && knowledge.disagreesWithRule(entry.gender(), origin)) {
            return messages.get(
                    MessageKey.NAMES_STYLE_GENDER_RULE_VERSUS_MODEL,
                    label,
                    GlossaryLabels.gender(messages, knowledge.gender()));
        }
        if (knowledge != null && knowledge.isLowConfidenceFor(entry.gender(), origin)) {
            return messages.get(MessageKey.NAMES_STYLE_GENDER_LOW_CONFIDENCE, label);
        }
        return withOrigin(messages, label, origin);
    }

    private static String withOrigin(final Messages messages, final String value, final Origin origin) {
        return messages.get(MessageKey.NAMES_STYLE_WITH_ORIGIN, value, GlossaryLabels.origin(messages, origin));
    }
}

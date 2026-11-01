package ua.bookloom.ui.state;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.EntryChanges;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.ui.i18n.GlossaryLabels;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/** Words a change of a glossary entry or a recurring term the way the results dialog lists it, numbering the rows from zero. */
final class ChangeRows {

    /** What stands for "no row" on the side of an added or removed row. */
    static final String DASH = "—";

    private static final String SEPARATOR = " · ";

    private final Messages messages;
    private long next;

    ChangeRows(final Messages messages) {
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    ChangeRow added(final GlossaryEntry entry) {
        return row(
                ChangeSection.NAMES,
                ChangeKind.ADDED,
                entry.term(),
                DASH,
                describe(entry),
                null,
                ChangeOrigin.MODEL,
                editable(entry));
    }

    // A name the pass found and could not give a target: the person types one in the results.
    ChangeRow untargeted(final GlossaryEntry entry) {
        return row(
                ChangeSection.NAMES,
                ChangeKind.UNTARGETED,
                entry.term(),
                describe(entry),
                DASH,
                null,
                ChangeOrigin.MODEL,
                editable(entry));
    }

    ChangeRow removed(final EntryChanges.Removal<GlossaryEntry> removal) {
        final GlossaryEntry entry = removal.entry();
        return row(
                ChangeSection.NAMES,
                ChangeKind.REMOVED,
                entry.term(),
                describe(entry),
                DASH,
                removal.reason(),
                ChangeOrigin.MODEL);
    }

    ChangeRow changed(final EntryChanges.Change<GlossaryEntry> change) {
        return row(
                ChangeSection.NAMES,
                ChangeKind.CHANGED,
                change.after().term(),
                describe(change.before()),
                describe(change.after()),
                change.reason(),
                ChangeOrigin.MODEL,
                editable(change.after()));
    }

    // Accept all changes only a target's state, so its rows show the target with its state.
    ChangeRow accepted(final EntryChanges.Change<GlossaryEntry> change) {
        return row(
                ChangeSection.NAMES,
                ChangeKind.CHANGED,
                change.after().term(),
                stated(change.before()),
                stated(change.after()),
                null,
                ChangeOrigin.MODEL);
    }

    ChangeRow left(final GlossaryEntry entry, final String reason) {
        return row(
                ChangeSection.NAMES,
                ChangeKind.LEFT,
                entry.term(),
                stated(entry),
                stated(entry),
                reason,
                ChangeOrigin.MODEL);
    }

    private String stated(final GlossaryEntry entry) {
        return String.join(
                SEPARATOR,
                Objects.requireNonNullElse(entry.target(), DASH),
                GlossaryLabels.state(messages, entry.targetState()));
    }

    ChangeRow addedTerm(final LexiconEntry entry, final ChangeOrigin origin) {
        return row(
                ChangeSection.TERMS,
                ChangeKind.ADDED,
                entry.term(),
                DASH,
                describe(entry),
                null,
                origin,
                editable(entry));
    }

    ChangeRow removedTerm(final EntryChanges.Removal<LexiconEntry> removal) {
        final LexiconEntry entry = removal.entry();
        return row(
                ChangeSection.TERMS,
                ChangeKind.REMOVED,
                entry.term(),
                describe(entry),
                DASH,
                removal.reason(),
                ChangeOrigin.MODEL);
    }

    ChangeRow changedTerm(final EntryChanges.Change<LexiconEntry> change) {
        return row(
                ChangeSection.TERMS,
                ChangeKind.CHANGED,
                change.after().term(),
                describe(change.before()),
                describe(change.after()),
                change.reason(),
                ChangeOrigin.MODEL,
                editable(change.after()));
    }

    private ChangeRow row(
            final ChangeSection section,
            final ChangeKind kind,
            final String term,
            final String before,
            final String after,
            final @Nullable String reason,
            final ChangeOrigin origin) {
        return row(section, kind, term, before, after, reason, origin, null);
    }

    private ChangeRow row(
            final ChangeSection section,
            final ChangeKind kind,
            final String term,
            final String before,
            final String after,
            final @Nullable String reason,
            final ChangeOrigin origin,
            final @Nullable EditableTarget editable) {
        return new ChangeRow(next++, kind, term, before, after, reason, origin, section, editable);
    }

    private EditableTarget editable(final GlossaryEntry entry) {
        return new EditableTarget(entry.id(), Objects.requireNonNullElse(entry.target(), ""), details(entry));
    }

    private static EditableTarget editable(final LexiconEntry entry) {
        return new EditableTarget(entry.term(), entry.established().orElse(""), "");
    }

    private String describe(final GlossaryEntry entry) {
        final String target = entry.target() == null || entry.target().isBlank()
                ? messages.get(MessageKey.NAMES_STYLE_NO_TARGET)
                : entry.target();
        return String.join(SEPARATOR, target, details(entry));
    }

    // The words after the target: the type, and the gender when one is known.
    private String details(final GlossaryEntry entry) {
        final List<String> parts = new ArrayList<>();
        parts.add(GlossaryLabels.type(messages, entry.type()));
        if (entry.gender() != Gender.UNKNOWN) {
            final String gender = GlossaryLabels.gender(messages, entry.gender());
            parts.add(
                    entry.isGenderSuggested() ? messages.get(MessageKey.NAMES_STYLE_GENDER_SUGGESTED, gender) : gender);
        }
        return String.join(SEPARATOR, parts);
    }

    private static String describe(final LexiconEntry entry) {
        return entry.established().orElse(DASH);
    }
}

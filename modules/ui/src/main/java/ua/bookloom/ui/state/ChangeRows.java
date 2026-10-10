package ua.bookloom.ui.state;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
        return new ChangeRow(next++, ChangeKind.ADDED, entry.term(), DASH, describe(entry), null);
    }

    ChangeRow removed(final EntryChanges.Removal<GlossaryEntry> removal) {
        final GlossaryEntry entry = removal.entry();
        return new ChangeRow(next++, ChangeKind.REMOVED, entry.term(), describe(entry), DASH, removal.reason());
    }

    ChangeRow changed(final EntryChanges.Change<GlossaryEntry> change) {
        return new ChangeRow(
                next++,
                ChangeKind.CHANGED,
                change.after().term(),
                describe(change.before()),
                describe(change.after()),
                change.reason());
    }

    ChangeRow addedTerm(final LexiconEntry entry) {
        return new ChangeRow(next++, ChangeKind.ADDED, entry.term(), DASH, describe(entry), null);
    }

    ChangeRow removedTerm(final EntryChanges.Removal<LexiconEntry> removal) {
        final LexiconEntry entry = removal.entry();
        return new ChangeRow(next++, ChangeKind.REMOVED, entry.term(), describe(entry), DASH, removal.reason());
    }

    ChangeRow changedTerm(final EntryChanges.Change<LexiconEntry> change) {
        return new ChangeRow(
                next++,
                ChangeKind.CHANGED,
                change.after().term(),
                describe(change.before()),
                describe(change.after()),
                change.reason());
    }

    private String describe(final GlossaryEntry entry) {
        final List<String> parts = new ArrayList<>();
        parts.add(
                entry.target() == null || entry.target().isBlank()
                        ? messages.get(MessageKey.NAMES_STYLE_FLAG_NO_TARGET)
                        : entry.target());
        parts.add(GlossaryLabels.type(messages, entry.type()));
        if (entry.gender() != Gender.UNKNOWN) {
            parts.add(GlossaryLabels.gender(messages, entry.gender()));
        }
        return String.join(SEPARATOR, parts);
    }

    private static String describe(final LexiconEntry entry) {
        return entry.established().orElse(DASH);
    }
}

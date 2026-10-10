package ua.bookloom.ui.state;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.EntryChanges;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.ui.i18n.Messages;

/**
 * Turns the exact changes of an operation into the results the screen shows: the rows with their wording and their
 * means to be reverted, the marks on the changed rows, and the one results object the dialog opens on. Each operation
 * replaces the last one's marks and results. FX thread only.
 */
@Slf4j
final class ChangeLog {

    private final ChangeReverts reverts;
    private final ChangeMarks marks;
    private final Messages messages;
    private final ActivityTracker activities;
    private LiveCalls callsBefore = LiveCalls.EMPTY;
    private final ReadOnlyObjectWrapper<@Nullable ChangeResults> results = new ReadOnlyObjectWrapper<>();

    ChangeLog(
            final ChangeReverts reverts,
            final ChangeMarks marks,
            final Messages messages,
            final ActivityTracker activities) {
        this.reverts = Objects.requireNonNull(reverts, "reverts");
        this.marks = Objects.requireNonNull(marks, "marks");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.activities = Objects.requireNonNull(activities, "activities");
    }

    ReadOnlyObjectProperty<@Nullable ChangeResults> results() {
        return results.getReadOnlyProperty();
    }

    /** Forgets the last operation's marks and results; called as the next one starts, or the screen opens again. */
    void begin() {
        log.debug("the last operation's results and marks are dropped");
        marks.clear();
        results.set(null);
        callsBefore = activities.lastCalls().get();
    }

    void stopped(final ChangeOperation operation) {
        log.info("{} stopped: nothing changed", operation);
        results.set(new ChangeResults(operation, true, List.of(), LiveCalls.EMPTY));
    }

    void names(final ChangeOperation operation, final EntryChanges<GlossaryEntry> changes) {
        final ChangeRows rows = new ChangeRows(messages);
        final List<ChangeResults.Item> items = new ArrayList<>();
        changes.added().forEach(entry -> {
            items.add(new ChangeResults.Item(rows.added(entry), reverts.removeName(entry)));
            marks.glossary().mark(entry.id());
        });
        changes.removed()
                .forEach(removal ->
                        items.add(new ChangeResults.Item(rows.removed(removal), reverts.addName(removal.entry()))));
        changes.changed().forEach(change -> {
            items.add(new ChangeResults.Item(rows.changed(change), reverts.setNameBack(change)));
            marks.glossary().mark(change.after().id());
        });
        publish(operation, items);
    }

    /**
     * Publishes the changes of a recurring-terms operation.
     *
     * @param operation the operation
     * @param found the terms its text step found before the model was asked, listed first; empty for the others
     * @param changes what the model step did
     */
    void terms(
            final ChangeOperation operation, final List<LexiconEntry> found, final EntryChanges<LexiconEntry> changes) {
        final ChangeRows rows = new ChangeRows(messages);
        final List<ChangeResults.Item> items = new ArrayList<>();
        found.forEach(entry -> addTerm(items, rows.addedTerm(entry, ChangeOrigin.TEXT), entry));
        changes.added().forEach(entry -> addTerm(items, rows.addedTerm(entry, ChangeOrigin.MODEL), entry));
        changes.removed()
                .forEach(removal -> items.add(new ChangeResults.Item(
                        rows.removedTerm(removal), reverts.restoreTerm(removal.entry(), false))));
        changes.changed().forEach(change -> {
            items.add(new ChangeResults.Item(rows.changedTerm(change), reverts.restoreTerm(change)));
            marks.terms().mark(LexiconEntry.keyOf(change.after().term()));
        });
        publish(operation, items);
    }

    private void addTerm(final List<ChangeResults.Item> items, final ChangeRow row, final LexiconEntry entry) {
        items.add(new ChangeResults.Item(row, reverts.removeTerm(entry)));
        marks.terms().mark(LexiconEntry.keyOf(entry.term()));
    }

    // The tracker keeps the calls of the last work that made any; they are this operation's only if it made some.
    private void publish(final ChangeOperation operation, final List<ChangeResults.Item> items) {
        log.info("{} changed {} rows", operation, items.size());
        final LiveCalls last = activities.lastCalls().get();
        results.set(new ChangeResults(operation, false, items, last.equals(callsBefore) ? LiveCalls.EMPTY : last));
    }
}

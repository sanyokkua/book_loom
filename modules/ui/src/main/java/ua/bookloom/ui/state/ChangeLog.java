package ua.bookloom.ui.state;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.SetChangeListener;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.EntryChanges;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.ui.i18n.MessageKey;
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
    private RunEnd end = RunEnd.NONE;
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
        end = RunEnd.NONE;
    }

    /** Keeps what the operation's activity showed, for the results the operation is about to publish. */
    void ended(final RunEnd shown) {
        end = Objects.requireNonNull(shown, "shown");
    }

    void stopped(final ChangeOperation operation) {
        log.info("{} stopped: nothing changed", operation);
        results.set(new ChangeResults(operation, true, List.of(), LiveCalls.EMPTY));
    }

    void names(final ChangeOperation operation, final EntryChanges<GlossaryEntry> changes) {
        final List<ChangeResults.Item> items = new ArrayList<>();
        nameItems(new ChangeRows(messages), items, changes);
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
        final List<ChangeResults.Item> items = new ArrayList<>();
        termItems(new ChangeRows(messages), items, found, changes);
        publish(operation, items);
    }

    /**
     * Publishes the changes of an operation that changed both tables as one list, the names first; the rows are
     * numbered across both so that each is reverted alone.
     *
     * @param operation the operation
     * @param names what it did to the glossary
     * @param terms what it did to the recurring terms
     * @param untargeted the glossary terms it left without a target, for the dialog to list
     */
    void both(
            final ChangeOperation operation,
            final EntryChanges<GlossaryEntry> names,
            final EntryChanges<LexiconEntry> terms,
            final List<String> untargeted) {
        final ChangeRows rows = new ChangeRows(messages);
        final List<ChangeResults.Item> items = new ArrayList<>();
        nameItems(rows, items, names);
        termItems(rows, items, List.of(), terms);
        publish(operation, items, untargeted);
    }

    private void nameItems(
            final ChangeRows rows, final List<ChangeResults.Item> items, final EntryChanges<GlossaryEntry> changes) {
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
    }

    private void termItems(
            final ChangeRows rows,
            final List<ChangeResults.Item> items,
            final List<LexiconEntry> found,
            final EntryChanges<LexiconEntry> changes) {
        found.forEach(entry -> addTerm(items, rows.addedTerm(entry, ChangeOrigin.TEXT), entry));
        changes.added().forEach(entry -> addTerm(items, rows.addedTerm(entry, ChangeOrigin.MODEL), entry));
        changes.removed()
                .forEach(removal -> items.add(new ChangeResults.Item(
                        rows.removedTerm(removal), reverts.restoreTerm(removal.entry(), false))));
        changes.changed().forEach(change -> {
            items.add(new ChangeResults.Item(rows.changedTerm(change), reverts.restoreTerm(change)));
            marks.terms().mark(LexiconEntry.keyOf(change.after().term()));
        });
    }

    private void addTerm(final List<ChangeResults.Item> items, final ChangeRow row, final LexiconEntry entry) {
        items.add(new ChangeResults.Item(row, reverts.removeTerm(entry)));
        marks.terms().mark(LexiconEntry.keyOf(entry.term()));
    }

    // The tracker keeps the calls of the last work that made any; they are this operation's only if it made some.
    private void publish(final ChangeOperation operation, final List<ChangeResults.Item> items) {
        publish(operation, items, List.of());
    }

    private void publish(
            final ChangeOperation operation, final List<ChangeResults.Item> items, final List<String> untargeted) {
        log.info("{} changed {} rows, left {} names without a target", operation, items.size(), untargeted.size());
        final LiveCalls last = activities.lastCalls().get();
        final ChangeResults published = new ChangeResults(
                operation,
                false,
                items,
                last.equals(callsBefore) ? LiveCalls.EMPTY : last,
                untargeted,
                end.steps(),
                end.elapsed(),
                end.hidden());
        followUndo(published);
        results.set(published);
    }

    // The line that announced the operation is no longer true once every row it changed is put back, so it says so.
    private void followUndo(final ChangeResults published) {
        final int changed = published.rows().size();
        if (changed == 0) {
            return;
        }
        published.reverted().addListener((SetChangeListener<Long>) change -> {
            if (published.reverted().size() == changed && results.get() == published) {
                log.info("every one of the {} rows of {} was put back", changed, published.operation());
                reverts.notice()
                        .set(new GlossaryNotice(
                                GlossaryNotice.Level.INFO,
                                messages.get(
                                        MessageKey.RESULTS_UNDONE_ALL,
                                        messages.get(published.operation().label()))));
            }
        });
    }
}

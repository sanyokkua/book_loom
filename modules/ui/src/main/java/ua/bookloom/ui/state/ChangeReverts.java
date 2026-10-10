package ua.bookloom.ui.state;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.beans.property.ObjectProperty;
import javafx.collections.ObservableList;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.GlossaryService;
import ua.bookloom.api.pipeline.LexiconService;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;

/**
 * Puts one row of an operation's results back through the same service calls the screen's own edits use: an added row
 * is removed, a removed row is added again with its entry, a changed row is set back to what it was. The screen's rows
 * and marks follow once the service has stored it; a refusal is reported in place and leaves the row as it is. FX thread
 * only.
 *
 * @param glossary the port the glossary rows are written through
 * @param lexicon the port the recurring terms are written through
 * @param calls the background calls the writes run on
 * @param rows the glossary table's rows
 * @param lexiconRows the recurring-terms card's rows
 * @param marks the rows marked as changed by the last operation
 * @param notice the line the screen reports in place
 * @param project the project the screen shows now
 */
@Slf4j
record ChangeReverts(
        GlossaryService glossary,
        LexiconService lexicon,
        GlossaryCalls calls,
        ObservableList<GlossaryEntry> rows,
        ObservableList<LexiconEntry> lexiconRows,
        ChangeMarks marks,
        ObjectProperty<@Nullable GlossaryNotice> notice,
        Supplier<String> project) {

    ChangeResults.Reversal removeName(final GlossaryEntry added) {
        return done -> write(
                "revert added name",
                () -> glossary.remove(added.projectId(), added.id()),
                added.projectId(),
                gone -> {
                    rows.removeIf(row -> row.id().equals(added.id()));
                    marks.glossary().unmark(added.id());
                },
                done);
    }

    ChangeResults.Reversal addName(final GlossaryEntry removed) {
        return done -> write(
                "revert removed name",
                () -> glossary.add(removed),
                removed.projectId(),
                back -> {
                    if (GlossaryEdits.indexOf(rows, back.id()) < 0) {
                        rows.add(back);
                    }
                },
                done);
    }

    ChangeResults.Reversal setNameBack(final GlossaryEntry before) {
        return done -> write(
                "revert changed name",
                () -> glossary.update(before),
                before.projectId(),
                stored -> {
                    final int at = GlossaryEdits.indexOf(rows, stored.id());
                    if (at >= 0) {
                        rows.set(at, stored);
                    }
                    marks.glossary().unmark(stored.id());
                },
                done);
    }

    ChangeResults.Reversal removeTerm(final LexiconEntry added) {
        return done -> write(
                "revert added term",
                () -> lexicon.remove(added.projectId(), added.term()),
                added.projectId(),
                gone -> {
                    final String key = LexiconEntry.keyOf(added.term());
                    lexiconRows.removeIf(row -> LexiconEntry.keyOf(row.term()).equals(key));
                    marks.terms().unmark(key);
                },
                done);
    }

    ChangeResults.Reversal restoreTerm(final LexiconEntry before, final boolean wasMarked) {
        return done -> write(
                "revert term",
                () -> lexicon.restore(before),
                before.projectId(),
                stored -> {
                    final String key = LexiconEntry.keyOf(stored.term());
                    final int at = indexOfTerm(key);
                    if (at >= 0) {
                        lexiconRows.set(at, stored);
                    } else {
                        lexiconRows.add(stored);
                    }
                    if (wasMarked) {
                        marks.terms().unmark(key);
                    }
                },
                done);
    }

    private int indexOfTerm(final String key) {
        for (int at = 0; at < lexiconRows.size(); at++) {
            if (LexiconEntry.keyOf(lexiconRows.get(at).term()).equals(key)) {
                return at;
            }
        }
        return -1;
    }

    private <T> void write(
            final String what,
            final Supplier<Result<T>> work,
            final String projectId,
            final Consumer<T> onStored,
            final Consumer<Boolean> done) {
        log.debug("{} of project {} started", what, projectId);
        calls.run(what, work, answer -> {
            final AppError failure = answer.error();
            if (failure != null) {
                log.warn("{} failed with {}", what, failure.code());
                notice.set(new GlossaryNotice(GlossaryNotice.Level.ERROR, failure.message()));
                done.accept(false);
                return;
            }
            if (projectId.equals(project.get())) {
                onStored.accept(Objects.requireNonNull(answer.data(), "data"));
            }
            done.accept(true);
        });
    }
}

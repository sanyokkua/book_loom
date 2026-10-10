package ua.bookloom.ui.state;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.beans.property.ObjectProperty;
import javafx.collections.ObservableList;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.LexiconService;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * The recurring-terms card's actions that need no model — load, scan, add, edit a rendering, promote, remove — as the
 * names and style screen runs them: on the background calls, each answer published only while the screen's opening that
 * asked is still current, and a failure reported in place. FX thread only.
 *
 * @param lexicon the port the terms are read and edited through
 * @param calls the background calls the work runs on
 * @param rows the card's rows
 * @param glossaryRows the glossary table's rows, where a promoted term joins and which an add checks for a duplicate
 * @param messages the catalogue a refusal is worded from
 * @param notice the line the screen reports in place
 */
@Slf4j
record LexiconActions(
        LexiconService lexicon,
        GlossaryCalls calls,
        ObservableList<LexiconEntry> rows,
        ObservableList<GlossaryEntry> glossaryRows,
        Messages messages,
        ObjectProperty<@Nullable GlossaryNotice> notice) {

    /** Reads the lexicon and, when it is empty, runs the deterministic scan, as the glossary does for names. */
    void show(final String project, final Consumer<Runnable> whileCurrent) {
        log.debug("showing the recurring terms of project {}", project);
        calls.run("lexicon load", () -> loadOrScan(project), answer -> whileCurrent.accept(() -> setAll(answer)));
    }

    /** Adds a typed term, refusing a blank or a duplicate before the service is asked. */
    void add(final String project, final String typed, final Consumer<Runnable> whileCurrent) {
        final String term = typed.strip();
        if (term.isEmpty() || isHeld(term)) {
            log.debug("adding a recurring term to project {} refused up front: blank={}", project, term.isEmpty());
            if (!term.isEmpty()) {
                notice.set(new GlossaryNotice(
                        GlossaryNotice.Level.ERROR, messages.get(MessageKey.RECURRING_DUPLICATE, term)));
            }
            return;
        }
        calls.run(
                "lexicon add",
                () -> lexicon.add(project, term),
                answer -> whileCurrent.accept(() -> settle("add", answer, added -> rows.add(added))));
    }

    /** Sets the person's rendering of a row; a blank text clears it. */
    void setRendering(
            final String project, final String term, final String text, final Consumer<Runnable> whileCurrent) {
        log.debug("choosing a rendering for a recurring term of project {}", project);
        calls.run(
                "lexicon edit",
                () -> lexicon.edit(project, term, text.isBlank() ? null : text.strip()),
                answer -> whileCurrent.accept(() -> settle("edit", answer, edited -> replace(edited))));
    }

    /** Moves a row to the glossary with its established rendering as the target. */
    void promote(final String project, final String term, final Consumer<Runnable> whileCurrent) {
        log.debug("promoting a recurring term of project {} to the glossary", project);
        calls.run(
                "lexicon promote",
                () -> lexicon.promote(project, term),
                answer -> whileCurrent.accept(() -> settle("promote", answer, entry -> {
                    removeRow(term);
                    glossaryRows.add(entry);
                })));
    }

    /** Removes a row's term from the list. */
    void remove(final String project, final String term, final Consumer<Runnable> whileCurrent) {
        log.debug("removing a recurring term of project {}", project);
        calls.run(
                "lexicon remove",
                () -> lexicon.remove(project, term),
                answer -> whileCurrent.accept(() -> settle("remove", answer, removed -> removeRow(term))));
    }

    private Result<List<LexiconEntry>> loadOrScan(final String project) {
        final Result<List<LexiconEntry>> held = lexicon.entries(project);
        if (held.isErr() || !Objects.requireNonNull(held.data(), "held").isEmpty()) {
            return held;
        }
        log.debug("the lexicon of project {} is empty: running the deterministic scan", project);
        return lexicon.scan(project).flatMap(done -> lexicon.entries(project));
    }

    private boolean isHeld(final String term) {
        final String key = LexiconEntry.keyOf(term);
        return rows.stream().anyMatch(row -> LexiconEntry.keyOf(row.term()).equals(key))
                || glossaryRows.stream()
                        .anyMatch(row -> LexiconEntry.keyOf(row.term()).equals(key));
    }

    private void replace(final LexiconEntry edited) {
        final String key = LexiconEntry.keyOf(edited.term());
        for (int at = 0; at < rows.size(); at++) {
            if (LexiconEntry.keyOf(rows.get(at).term()).equals(key)) {
                rows.set(at, edited);
                return;
            }
        }
        rows.add(edited);
    }

    private void removeRow(final String term) {
        final String key = LexiconEntry.keyOf(term);
        rows.removeIf(row -> LexiconEntry.keyOf(row.term()).equals(key));
    }

    private void setAll(final Result<List<LexiconEntry>> answer) {
        settle("load", answer, all -> rows.setAll(all));
    }

    private <T> void settle(final String what, final Result<T> answer, final Consumer<T> onOk) {
        final AppError failure = answer.error();
        if (failure != null) {
            log.warn("the recurring terms {} failed with {}", what, failure.code());
            notice.set(new GlossaryNotice(GlossaryNotice.Level.ERROR, failure.message()));
            return;
        }
        notice.set(null);
        onOk.accept(Objects.requireNonNull(answer.data(), "data"));
    }
}

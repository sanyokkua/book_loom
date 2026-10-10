package ua.bookloom.ui.state;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.collections.ObservableList;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.GlossaryService;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * One edit of a glossary row as the names and style screen makes it: the row is changed, an edit that changes nothing is
 * ignored, the service stores it, and a refused edit leaves the stored row as it was and raises the restorations counter
 * so a cell showing what the person typed learns to show the stored value again. FX thread only.
 *
 * @param glossary the port the edit is stored through
 * @param calls the background calls the store runs on
 * @param rows the screen's rows
 * @param messages the catalogue a refusal is worded from
 * @param notice the line the screen reports in place
 * @param restorations counts the refused edits that were undone
 */
@Slf4j
record GlossaryRowEdits(
        GlossaryService glossary,
        GlossaryCalls calls,
        ObservableList<GlossaryEntry> rows,
        Messages messages,
        ObjectProperty<@Nullable GlossaryNotice> notice,
        ReadOnlyIntegerWrapper restorations) {

    void change(
            final String entryId,
            final String field,
            final UnaryOperator<GlossaryEntry> edit,
            final Consumer<Runnable> whileCurrent) {
        Objects.requireNonNull(entryId, "entryId");
        final int at = GlossaryEdits.indexOf(rows, entryId);
        if (at < 0) {
            log.debug("edit of {} on entry {} ignored: no such row", field, entryId);
            return;
        }
        final GlossaryEntry changed = edit.apply(rows.get(at));
        if (changed.equals(rows.get(at))) {
            log.debug("edit of {} on entry {} ignored: nothing changed", field, entryId);
            return;
        }
        log.debug("editing {} of entry {}", field, entryId);
        log.trace("entry {} becomes term '{}' target '{}'", entryId, changed.term(), changed.target());
        calls.run(
                "update",
                () -> glossary.update(changed),
                answer -> whileCurrent.accept(() -> updated(entryId, answer)));
    }

    void remove(final String project, final String entryId, final Consumer<Runnable> whileCurrent) {
        log.debug("removing entry {} of project {}", entryId, project);
        calls.run(
                "remove",
                () -> glossary.remove(project, entryId),
                answer -> whileCurrent.accept(() -> removed(entryId, answer)));
    }

    private void removed(final String entryId, final Result<Boolean> answer) {
        final AppError failure = answer.error();
        if (failure != null) {
            log.warn("the glossary remove failed with {}", failure.code());
            notice.set(new GlossaryNotice(GlossaryNotice.Level.ERROR, failure.message()));
            return;
        }
        rows.removeIf(row -> row.id().equals(entryId));
    }

    private void updated(final String entryId, final Result<GlossaryEntry> answer) {
        final int at = GlossaryEdits.indexOf(rows, entryId);
        final AppError failure = answer.error();
        if (failure == null) {
            if (at >= 0) {
                rows.set(at, Objects.requireNonNull(answer.data(), "data"));
            }
            notice.set(null);
            return;
        }
        refuse(entryId, failure);
        if (at >= 0) {
            rows.set(at, rows.get(at));
        }
        restorations.set(restorations.get() + 1);
    }

    private void refuse(final String entryId, final AppError failure) {
        if (failure.code() == ErrorCode.validation) {
            log.warn("edit of entry {} refused: a locked term needs a target", entryId);
            notice.set(new GlossaryNotice(
                    GlossaryNotice.Level.ERROR, messages.get(MessageKey.NAMES_STYLE_LOCK_NEEDS_TARGET)));
            return;
        }
        log.warn("the glossary update failed with {}", failure.code());
        notice.set(new GlossaryNotice(GlossaryNotice.Level.ERROR, failure.message()));
    }
}

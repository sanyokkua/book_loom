package ua.bookloom.ui.state;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.EntryChanges;
import ua.bookloom.api.pipeline.GlossaryService;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;

/**
 * "Confirm suggested genders": every character whose gender is only a suggestion becomes the person's, in one action
 * that reports its changes like a model operation does, so the results list can put each back or all of them. Nothing
 * is asked of a model. FX thread only.
 */
@Slf4j
final class GenderConfirmations {

    private final GlossaryService glossary;
    private final GlossaryCalls calls;
    private final ObservableList<GlossaryEntry> rows;
    private final ObjectProperty<@Nullable GlossaryNotice> notice;
    private final ChangeLog changes;
    private final Supplier<String> project;
    private final ReadOnlyIntegerWrapper suggestedCount = new ReadOnlyIntegerWrapper();

    GenderConfirmations(
            final GlossaryService glossary,
            final GlossaryCalls calls,
            final ObservableList<GlossaryEntry> rows,
            final ObjectProperty<@Nullable GlossaryNotice> notice,
            final ChangeLog changes,
            final Supplier<String> project) {
        this.glossary = glossary;
        this.calls = calls;
        this.rows = rows;
        this.notice = notice;
        this.changes = changes;
        this.project = project;
        rows.addListener((ListChangeListener<GlossaryEntry>) change -> suggestedCount.set((int)
                rows.stream().filter(GenderConfirmations::isSuggestedCharacter).count()));
    }

    ReadOnlyIntegerProperty suggestedCount() {
        return suggestedCount.getReadOnlyProperty();
    }

    /** The rows that would be confirmed: characters whose known gender is a suggestion. */
    static boolean isSuggestedCharacter(final GlossaryEntry entry) {
        return entry.type() == TermType.CHARACTER && entry.isGenderSuggested();
    }

    /** Confirms them all; does nothing, and publishes nothing, when none is suggested. */
    void confirmAll() {
        final String shown = project.get();
        final List<GlossaryEntry> suggested =
                rows.stream().filter(GenderConfirmations::isSuggestedCharacter).toList();
        if (suggested.isEmpty()) {
            log.debug("no suggested character gender to confirm in project {}", shown);
            return;
        }
        log.info("confirming {} suggested character genders of project {}", suggested.size(), shown);
        changes.begin();
        calls.run("confirm genders", () -> store(suggested), answer -> published(shown, answer));
    }

    // Runs on the background executor: each row is stored in turn, and the first refusal ends the action with what was
    // stored so far reported, so a half-done confirmation can still be undone row by row.
    private Result<EntryChanges<GlossaryEntry>> store(final List<GlossaryEntry> suggested) {
        final List<EntryChanges.Change<GlossaryEntry>> done = new ArrayList<>();
        for (final GlossaryEntry before : suggested) {
            final Result<GlossaryEntry> stored = glossary.update(before.withGender(before.gender()));
            final AppError failure = stored.error();
            if (failure != null) {
                log.warn("confirming the gender of one character failed with {}", failure.code());
                return done.isEmpty() ? Result.err(failure) : Result.ok(changesOf(done));
            }
            done.add(new EntryChanges.Change<>(before, Objects.requireNonNull(stored.data(), "data"), null));
        }
        return Result.ok(changesOf(done));
    }

    private static EntryChanges<GlossaryEntry> changesOf(final List<EntryChanges.Change<GlossaryEntry>> done) {
        return new EntryChanges<>(List.of(), List.of(), done);
    }

    private void published(final String shown, final Result<EntryChanges<GlossaryEntry>> answer) {
        final AppError failure = answer.error();
        if (failure != null) {
            notice.set(new GlossaryNotice(GlossaryNotice.Level.ERROR, failure.message()));
            return;
        }
        final EntryChanges<GlossaryEntry> done = Objects.requireNonNull(answer.data(), "data");
        if (!shown.equals(project.get())) {
            log.debug("the confirmed genders of project {} are not shown: another project is open", shown);
            return;
        }
        final Consumer<EntryChanges.Change<GlossaryEntry>> show = change -> {
            final int at = GlossaryEdits.indexOf(rows, change.after().id());
            if (at >= 0) {
                rows.set(at, change.after());
            }
        };
        done.changed().forEach(show);
        changes.names(ChangeOperation.CONFIRM_GENDERS, done);
    }
}

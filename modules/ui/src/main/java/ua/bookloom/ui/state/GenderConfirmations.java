package ua.bookloom.ui.state;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        final List<GlossaryEntry> before = List.copyOf(rows);
        calls.run("confirm genders", () -> store(suggested, before), answer -> published(shown, answer));
    }

    /** What the action did: every row it changed, and the refusal that stopped it early, if one did. */
    private record Outcome(
            EntryChanges<GlossaryEntry> done, @Nullable AppError stoppedBy) {}

    // Runs on the background executor: each row is stored in turn and the first refusal ends the action. The service
    // shares a chosen gender with the character's other spellings, so what changed is read back from the glossary
    // rather than taken from the rows asked for; that is what lets Undo restore every row.
    private Result<Outcome> store(final List<GlossaryEntry> suggested, final List<GlossaryEntry> before) {
        final List<EntryChanges.Change<GlossaryEntry>> asked = new ArrayList<>();
        @Nullable AppError stoppedBy = null;
        for (final GlossaryEntry row : suggested) {
            final Result<GlossaryEntry> stored = glossary.update(row.withGender(row.gender()));
            stoppedBy = stored.error();
            if (stoppedBy != null) {
                log.warn("confirming the gender of one character stopped with {}", stoppedBy.code());
                break;
            }
            asked.add(new EntryChanges.Change<>(row, Objects.requireNonNull(stored.data(), "data"), null));
        }
        if (asked.isEmpty() && stoppedBy != null) {
            return Result.err(stoppedBy);
        }
        return Result.ok(
                new Outcome(changesOf(before, asked, suggested.getFirst().projectId()), stoppedBy));
    }

    private EntryChanges<GlossaryEntry> changesOf(
            final List<GlossaryEntry> before,
            final List<EntryChanges.Change<GlossaryEntry>> asked,
            final String projectId) {
        final Result<List<GlossaryEntry>> now = glossary.entries(projectId);
        if (now.isErr()) {
            log.debug("the glossary could not be read back: only the rows asked for are reported");
            return new EntryChanges<>(List.of(), List.of(), asked);
        }
        final Map<String, GlossaryEntry> held = new HashMap<>();
        before.forEach(entry -> held.put(entry.id(), entry));
        final List<EntryChanges.Change<GlossaryEntry>> changed = new ArrayList<>();
        for (final GlossaryEntry after : Objects.requireNonNull(now.data(), "data")) {
            final GlossaryEntry was = held.get(after.id());
            if (was != null && !was.equals(after)) {
                changed.add(new EntryChanges.Change<>(was, after, null));
            }
        }
        log.debug("{} rows asked for, {} rows changed", asked.size(), changed.size());
        return new EntryChanges<>(List.of(), List.of(), changed);
    }

    private void published(final String shown, final Result<Outcome> answer) {
        final AppError failure = answer.error();
        if (failure != null) {
            notice.set(new GlossaryNotice(GlossaryNotice.Level.ERROR, failure.message()));
            return;
        }
        final Outcome outcome = Objects.requireNonNull(answer.data(), "data");
        final EntryChanges<GlossaryEntry> done = outcome.done();
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
        final AppError stopped = outcome.stoppedBy();
        if (stopped != null) {
            log.warn(
                    "confirming genders stopped early after {} changed rows",
                    done.changed().size());
            notice.set(new GlossaryNotice(GlossaryNotice.Level.ERROR, stopped.message()));
        }
    }
}

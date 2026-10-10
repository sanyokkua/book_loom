package ua.bookloom.ui.state;

import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.EntryChanges;
import ua.bookloom.api.pipeline.GlossaryReviewReport;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.ui.i18n.MessageKey;

/**
 * What the screen shows when a model action of Names &amp; style answers: the rows it changed, the line that words the
 * outcome and the results the dialog lists. Kept apart from {@link GlossaryModelRuns}, which starts, stops and counts
 * the actions. FX thread only.
 */
@Slf4j
final class ModelAnswers {

    /** The recurring terms after an action: what its text step found, what its model step did and all of them now. */
    record Terms(List<LexiconEntry> found, EntryChanges<LexiconEntry> changes, List<LexiconEntry> all) {}

    private final GlossaryModelRuns.Screen screen;
    private final GlossaryCalls calls;

    ModelAnswers(final GlossaryModelRuns.Screen screen, final GlossaryCalls calls) {
        this.screen = Objects.requireNonNull(screen, "screen");
        this.calls = Objects.requireNonNull(calls, "calls");
    }

    Result<ModelAnswers.Terms> terms(
            final String project, final List<LexiconEntry> found, final EntryChanges<LexiconEntry> changes) {
        return screen.lexicon().entries(project).map(all -> new Terms(found, changes, all));
    }

    void termsChanged(final ChangeOperation operation, final Terms answer, final int changed, final MessageKey line) {
        log.info(
                "model action on recurring terms changed {} of {}",
                changed,
                answer.all().size());
        screen.lexiconRows().setAll(answer.all());
        info(screen.messages().get(line, changed));
        screen.changes().terms(operation, answer.found(), answer.changes());
    }

    // A term scan stores what its text step found before the model answers, so a scan that ends early still shows it.
    void reloadTerms(final ChangeOperation operation) {
        if (operation != ChangeOperation.TERM_SCAN) {
            return;
        }
        final String project = screen.project().get();
        log.debug("reloading the recurring terms of project {} after an unfinished scan", project);
        calls.run("lexicon reload", () -> screen.lexicon().entries(project), answer -> {
            final List<LexiconEntry> held = answer.data();
            if (held != null && project.equals(screen.project().get())) {
                screen.lexiconRows().setAll(held);
            }
        });
    }

    void scanned(final ChangeOperation operation, final EntryChanges<GlossaryEntry> changes) {
        log.info("model scan proposed {} entries", changes.added().size());
        changes.added().stream()
                .filter(entry -> GlossaryEdits.indexOf(screen.rows(), entry.id()) < 0)
                .forEach(screen.rows()::add);
        screen.changes().names(operation, changes);
    }

    void reviewed(final ChangeOperation operation, final GlossaryReviewReport report) {
        log.info(
                "model review removed {}, updated {} and suggested targets for {} entries",
                report.removed(),
                report.updated(),
                report.suggested());
        screen.rows().setAll(report.entries());
        info(screen.messages()
                .get(MessageKey.NAMES_STYLE_REVIEWED, report.removed(), report.updated(), report.suggested()));
        screen.changes().names(operation, report.changes());
    }

    void renderingsSuggested(final ChangeOperation operation, final Terms answer) {
        final List<LexiconEntry> all = answer.all();
        final int rendered = (int)
                all.stream().filter(entry -> entry.established().isPresent()).count();
        log.info("model suggested renderings: {} of {} recurring terms have one", rendered, all.size());
        screen.lexiconRows().setAll(all);
        info(screen.messages().get(MessageKey.RECURRING_SUGGESTED, rendered));
        screen.changes().terms(operation, answer.found(), answer.changes());
    }

    void translated(final ChangeOperation operation, final EntryChanges<GlossaryEntry> changes) {
        log.info(
                "model translate suggested targets for {} entries",
                changes.changed().size());
        changes.changed().forEach(change -> {
            final int at = GlossaryEdits.indexOf(screen.rows(), change.after().id());
            if (at >= 0) {
                screen.rows().set(at, change.after());
            }
        });
        info(screen.messages()
                .get(MessageKey.NAMES_STYLE_TRANSLATED, changes.changed().size()));
        screen.changes().names(operation, changes);
    }

    private void info(final String text) {
        screen.notice().set(new GlossaryNotice(GlossaryNotice.Level.INFO, text));
    }
}

package ua.bookloom.ui.state;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import ua.bookloom.api.project.LexiconEntry;

/**
 * What the Recurring terms card of Names &amp; style shows and does: the rows, and the actions on them, each published
 * only while the opening of the screen that asked is still current. The model's suggestion of renderings runs through
 * the screen's model actions, so it is one at a time with the name scan and review. FX thread only.
 */
public final class RecurringTerms {

    private final LexiconActions actions;
    private final ObservableList<LexiconEntry> rows;
    private final Supplier<String> project;
    private final Supplier<Consumer<Runnable>> current;
    private final ModelActions model;

    /**
     * The screen's model actions on the recurring terms; each runs through the glossary's one-at-a-time model runs.
     *
     * @param suggest asks for a rendering of each term that has none
     * @param scan asks the model to choose recurring terms among the book's frequent words
     * @param review asks the model which held terms are worth keeping consistent
     */
    record ModelActions(Runnable suggest, Runnable scan, Runnable review) {}

    RecurringTerms(
            final LexiconActions actions,
            final Supplier<String> project,
            final Supplier<Consumer<Runnable>> current,
            final ModelActions model) {
        this.actions = Objects.requireNonNull(actions, "actions");
        this.rows = actions.rows();
        this.project = Objects.requireNonNull(project, "project");
        this.current = Objects.requireNonNull(current, "current");
        this.model = Objects.requireNonNull(model, "model");
    }

    /**
     * The rows, as the service listed them.
     *
     * @return a read-only list
     */
    public ObservableList<LexiconEntry> rows() {
        return FXCollections.unmodifiableObservableList(rows);
    }

    /** Scans the book for recurring terms and adds the new ones to the rows. */
    public void find() {
        actions.scan(project.get(), current.get());
    }

    /** Asks the chosen model for a rendering of each term that has none. */
    public void suggestRenderings() {
        model.suggest().run();
    }

    /** Asks the chosen model to choose recurring terms among the book's frequent words and adds them. */
    public void scanWithModel() {
        model.scan().run();
    }

    /** Asks the chosen model which held terms are worth keeping consistent and removes the others. */
    public void reviewWithModel() {
        model.review().run();
    }

    /**
     * Adds a term by hand, refusing a blank one or one the glossary or the list holds.
     *
     * @param term the typed word or title
     */
    public void add(final String term) {
        Objects.requireNonNull(term, "term");
        actions.add(project.get(), term, current.get());
    }

    /**
     * Sets the rendering the person wants kept; a blank text clears it.
     *
     * @param term the row's source term
     * @param rendering the typed rendering
     */
    public void setRendering(final String term, final String rendering) {
        Objects.requireNonNull(term, "term");
        Objects.requireNonNull(rendering, "rendering");
        actions.setRendering(project.get(), term, rendering, current.get());
    }

    /**
     * Moves a term to the glossary with its rendering as the target.
     *
     * @param term the row's source term
     */
    public void promote(final String term) {
        Objects.requireNonNull(term, "term");
        actions.promote(project.get(), term, current.get());
    }

    /**
     * Removes a term from the list.
     *
     * @param term the row's source term
     */
    public void remove(final String term) {
        Objects.requireNonNull(term, "term");
        actions.remove(project.get(), term, current.get());
    }

    /** Loads the rows of a project, scanning when it has none; called as the screen opens. */
    void show(final String projectId) {
        actions.rows().clear();
        actions.show(projectId, current.get());
    }
}

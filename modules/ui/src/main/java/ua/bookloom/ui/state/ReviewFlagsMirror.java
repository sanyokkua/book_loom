package ua.bookloom.ui.state;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.pipeline.GlossaryService;
import ua.bookloom.api.pipeline.TargetIssue;
import ua.bookloom.api.project.GlossaryEntry;

/**
 * The review flags of the shown rows ({@code specs/glossary/spec.md} "Flag a target that is not a dictionary form"),
 * asked for again whenever the rows change, since the service computes them each time and stores none. One request runs
 * at a time; a change while it runs asks once more when it ends, so the flags follow the last rows. A failed request
 * keeps the flags that were shown, and unchanged flags redraw nothing, so a cell being typed in keeps its focus. FX
 * thread only.
 */
@Slf4j
final class ReviewFlagsMirror {

    private final GlossaryService glossary;
    private final GlossaryCalls calls;
    private final Supplier<String> project;
    private final ObservableMap<String, List<TargetIssue>> flags = FXCollections.observableHashMap();
    private final ObservableMap<String, List<TargetIssue>> flagsView = FXCollections.unmodifiableObservableMap(flags);
    private final ReadOnlyIntegerWrapper refreshes = new ReadOnlyIntegerWrapper();
    private boolean asking;
    private boolean askAgain;

    ReviewFlagsMirror(
            final GlossaryService glossary,
            final GlossaryCalls calls,
            final ObservableList<GlossaryEntry> rows,
            final Supplier<String> project) {
        this.glossary = Objects.requireNonNull(glossary, "glossary");
        this.calls = Objects.requireNonNull(calls, "calls");
        this.project = Objects.requireNonNull(project, "project");
        rows.addListener((ListChangeListener<GlossaryEntry>) change -> refresh());
    }

    ObservableMap<String, List<TargetIssue>> flags() {
        return flagsView;
    }

    ReadOnlyIntegerProperty refreshes() {
        return refreshes.getReadOnlyProperty();
    }

    private void refresh() {
        if (asking) {
            askAgain = true;
            return;
        }
        final String asked = project.get();
        if (asked.isEmpty()) {
            return;
        }
        asking = true;
        calls.run("review flags", () -> glossary.reviewFlags(asked), answer -> {
            asking = false;
            final Map<String, List<TargetIssue>> found = answer.data();
            if (found == null) {
                log.debug("the review flags of project {} were not read; the shown ones stay", asked);
            } else if (asked.equals(project.get()) && !flags.equals(found)) {
                log.debug("project {} has {} flagged targets", asked, found.size());
                flags.clear();
                flags.putAll(found);
                refreshes.set(refreshes.get() + 1);
            }
            if (askAgain) {
                askAgain = false;
                refresh();
            }
        });
    }
}

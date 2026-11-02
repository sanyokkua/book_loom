package ua.bookloom.ui.state;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.GlossaryService;
import ua.bookloom.api.pipeline.TargetIssue;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.ui.i18n.Messages;

/**
 * What the glossary table holds that nobody has confirmed yet: suggested targets and genders, and the review flags the
 * service computes for the shown rows. FX thread only.
 */
public final class Suggestions {

    private final ReadOnlyIntegerWrapper suggestedCount = new ReadOnlyIntegerWrapper();
    private final GenderConfirmations genderConfirmations;
    private final TargetAcceptances acceptances;
    private final ReviewFlagsMirror flags;

    Suggestions(
            final GlossaryService glossary,
            final GlossaryCalls calls,
            final ObservableList<GlossaryEntry> rows,
            final ReadOnlyObjectWrapper<@Nullable GlossaryNotice> notice,
            final ChangeLog changeLog,
            final Supplier<String> project,
            final Messages messages) {
        Objects.requireNonNull(rows, "rows");
        this.genderConfirmations = new GenderConfirmations(glossary, calls, rows, notice, changeLog, project, messages);
        this.acceptances = new TargetAcceptances(glossary, calls, rows, notice, changeLog, project, messages);
        this.flags = new ReviewFlagsMirror(glossary, calls, rows, project);
        rows.addListener((ListChangeListener<GlossaryEntry>) change -> suggestedCount.set(
                (int) rows.stream().filter(GlossaryEntry::isSuggested).count()));
    }

    /** Accepts every suggested target that passes the target language's checks; the results list the rest. */
    public void acceptAll() {
        acceptances.acceptAll();
    }

    /** {@return the review flags of one shown row, computed by the service and never stored; empty when it has none} */
    public List<TargetIssue> reviewFlagsOf(final String entryId) {
        return flags.flags().getOrDefault(entryId, List.of());
    }

    /** {@return a read-only count of the times the review flags were read again, which only ever grows} */
    public ReadOnlyIntegerProperty reviewFlagRefreshes() {
        return flags.refreshes();
    }

    /** {@return a read-only count of the rows that hold a suggested target nobody confirmed} */
    public ReadOnlyIntegerProperty suggestedCount() {
        return suggestedCount.getReadOnlyProperty();
    }

    /** Turns every suggested character gender into the person's; the results list can put each back. */
    public void confirmGenders() {
        genderConfirmations.confirmAll();
    }

    /** {@return a read-only count of the characters whose gender is a suggestion nobody has confirmed} */
    public ReadOnlyIntegerProperty suggestedGenderCount() {
        return genderConfirmations.suggestedCount();
    }
}

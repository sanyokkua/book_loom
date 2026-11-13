package ua.bookloom.ui.state;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javafx.beans.property.ObjectProperty;
import javafx.collections.ObservableList;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.AcceptReport;
import ua.bookloom.api.pipeline.EntryChanges;
import ua.bookloom.api.pipeline.GlossaryService;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.ui.i18n.ErrorWords;
import ua.bookloom.ui.i18n.GlossaryLabels;
import ua.bookloom.ui.i18n.Messages;

/**
 * Accept all ({@code specs/glossary/spec.md} "Show a suggested target as a hint and let the person accept it"): one
 * call of {@link GlossaryService#acceptSuggested}, which checks every suggested target against the target language's
 * rules. The accepted rows are reported like a model operation's changes, so Undo all writes each back as it was, still
 * suggested; the rows left suggested are listed with their reasons. No model is asked. FX thread only.
 */
@Slf4j
final class TargetAcceptances {

    private final GlossaryService glossary;
    private final GlossaryCalls calls;
    private final ObservableList<GlossaryEntry> rows;
    private final ObjectProperty<@Nullable GlossaryNotice> notice;
    private final ChangeLog changes;
    private final Supplier<String> project;
    private final Messages messages;

    TargetAcceptances(
            final GlossaryService glossary,
            final GlossaryCalls calls,
            final ObservableList<GlossaryEntry> rows,
            final ObjectProperty<@Nullable GlossaryNotice> notice,
            final ChangeLog changes,
            final Supplier<String> project,
            final Messages messages) {
        this.glossary = glossary;
        this.calls = calls;
        this.rows = rows;
        this.notice = notice;
        this.changes = changes;
        this.project = project;
        this.messages = messages;
    }

    /** Accepts every suggested target that passes; does nothing, and publishes nothing, when none is suggested. */
    void acceptAll() {
        final String shown = project.get();
        final List<GlossaryEntry> before = List.copyOf(rows);
        final long suggested =
                before.stream().filter(GlossaryEntry::isSuggested).count();
        if (suggested == 0) {
            log.debug("no suggested target to accept in project {}", shown);
            return;
        }
        log.info("accepting {} suggested targets of project {}", suggested, shown);
        changes.begin();
        calls.run("accept targets", () -> glossary.acceptSuggested(shown), answer -> published(shown, before, answer));
    }

    private void published(final String shown, final List<GlossaryEntry> before, final Result<AcceptReport> answer) {
        final AppError failure = answer.error();
        if (failure != null) {
            log.warn("accept all of project {} failed with {}", shown, failure.code());
            notice.set(new GlossaryNotice(GlossaryNotice.Level.ERROR, ErrorWords.message(messages, failure)));
            return;
        }
        if (!shown.equals(project.get())) {
            log.debug("the accepted targets of project {} are not shown: another project is open", shown);
            return;
        }
        final AcceptReport report = Objects.requireNonNull(answer.data(), "data");
        final List<EntryChanges.Change<GlossaryEntry>> accepted = new ArrayList<>();
        report.accepted()
                .forEach(after -> heldBefore(before, after)
                        .ifPresent(was -> accepted.add(new EntryChanges.Change<>(was, after, null))));
        final List<ChangeLog.Left> left = report.rejected().stream()
                .map(rejected -> new ChangeLog.Left(rejected.entry(), reasons(rejected)))
                .toList();
        report.accepted().forEach(this::show);
        report.rejected().forEach(rejected -> show(rejected.entry()));
        log.info("accept all of project {}: {} accepted, {} left", shown, accepted.size(), left.size());
        changes.acceptance(new EntryChanges<>(List.of(), List.of(), accepted), left);
    }

    private static Optional<GlossaryEntry> heldBefore(final List<GlossaryEntry> before, final GlossaryEntry after) {
        return before.stream().filter(row -> row.id().equals(after.id())).findFirst();
    }

    private String reasons(final AcceptReport.Rejected rejected) {
        return rejected.issues().stream()
                .map(issue -> GlossaryLabels.issue(messages, issue))
                .distinct()
                .collect(Collectors.joining(", "));
    }

    private void show(final GlossaryEntry stored) {
        final int at = GlossaryEdits.indexOf(rows, stored.id());
        if (at >= 0) {
            rows.set(at, stored);
        }
    }
}

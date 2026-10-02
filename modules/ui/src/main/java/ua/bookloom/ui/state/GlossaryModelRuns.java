package ua.bookloom.ui.state;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.ObservableList;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatModelFactory;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.pipeline.BatchStarted;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.GlossaryReviewReport;
import ua.bookloom.api.pipeline.GlossaryService;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.ModelCallStarted;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * The glossary's model actions — the name scan and the review — as the names and style screen runs them: one at a time,
 * with the chosen model, a waiting line that counts the requests and, while targets are suggested, the batches, and a stop that interrupts the request in flight. A
 * stopped action changes nothing, since the service writes only after every request has answered. Neither starts while
 * other model work runs ({@link ActivityTracker}); each registers itself while it runs, so the title bar can name and
 * stop it and leaving the screen asks first.
 *
 * <p>An answer is shown for the project it started on even when the screen was opened again meanwhile, since the stored
 * glossary already holds its result. FX thread only.
 */
@Slf4j
final class GlossaryModelRuns {

    /** One model action: given the model and the receiver of its progress, its answer. */
    @FunctionalInterface
    private interface Work<T> {

        Result<T> run(ChatModel model, Consumer<JobEvent> progress);
    }

    /**
     * Where the actions read and show what they do.
     *
     * @param glossary the port the actions call
     * @param models where the chosen model is built
     * @param settings where the chosen provider and model are read
     * @param messages the catalogue the lines are worded from
     * @param rows the screen's rows
     * @param notice the line the screen reports in place
     * @param project the project the screen shows now
     * @param activities the model work under way, which a scan or review must not overlap
     */
    record Screen(
            GlossaryService glossary,
            ChatModelFactory models,
            SettingsViewModel settings,
            Messages messages,
            ObservableList<GlossaryEntry> rows,
            ObjectProperty<@Nullable GlossaryNotice> notice,
            Supplier<String> project,
            ActivityTracker activities) {}

    private final Screen screen;
    private final GlossaryCalls calls;
    private final ReadOnlyBooleanWrapper busy = new ReadOnlyBooleanWrapper();
    private final ReadOnlyStringWrapper blockedReason = new ReadOnlyStringWrapper("");
    private final ObjectBinding<@Nullable ActivityKind> blocker;
    private @Nullable Future<?> running;
    private ActivityTracker.@Nullable Handle handle;
    private long ticket;
    private int requests;
    private @Nullable BatchStarted suggesting;

    GlossaryModelRuns(final Screen screen, final GlossaryCalls calls) {
        this.screen = Objects.requireNonNull(screen, "screen");
        this.calls = Objects.requireNonNull(calls, "calls");
        // A scan and a review are ruled out by the same work, so the scan's blocker speaks for both.
        this.blocker = screen.activities().blocker(ActivityKind.GLOSSARY_SCAN);
        blocker.addListener(observed -> refreshBlocked());
        busy.addListener(observed -> refreshBlocked());
        refreshBlocked();
    }

    ReadOnlyBooleanProperty busy() {
        return busy.getReadOnlyProperty();
    }

    /** Why the model actions are not offered because other model work runs; empty otherwise. FX thread only. */
    ReadOnlyStringProperty blockedReason() {
        return blockedReason.getReadOnlyProperty();
    }

    private void refreshBlocked() {
        final ActivityKind kind = blocker.get();
        final String reason = kind == null || busy.get()
                ? ""
                : screen.messages()
                        .get(MessageKey.ACTIVITY_BLOCKED, screen.messages().get(kind.label()));
        if (!reason.equals(blockedReason.get())) {
            log.debug("glossary model actions blocked by {}", kind);
        }
        blockedReason.set(reason);
    }

    void scan() {
        final String project = screen.project().get();
        start(
                ActivityKind.GLOSSARY_SCAN,
                MessageKey.NAMES_STYLE_NO_MODEL,
                (model, progress) -> screen.glossary().prescan(project, model, progress),
                this::scanned);
    }

    void review() {
        final String project = screen.project().get();
        start(
                ActivityKind.GLOSSARY_REVIEW,
                MessageKey.NAMES_STYLE_NO_MODEL_REVIEW,
                (model, progress) -> screen.glossary().review(project, model, progress),
                this::reviewed);
    }

    /** Stops the action under way, if any; the glossary keeps what it had. */
    void stop() {
        final Future<?> current = running;
        if (current == null) {
            log.debug("stop of the model action ignored: none is running");
            return;
        }
        log.info("model action stopped by the person after {} requests", requests);
        current.cancel(true);
        running = null;
        ticket++;
        endActivity();
        busy.set(false);
        line(GlossaryNotice.Level.INFO, screen.messages().get(MessageKey.NAMES_STYLE_MODEL_STOPPED));
    }

    private void scanned(final List<GlossaryEntry> proposed) {
        log.info("model scan proposed {} entries", proposed.size());
        proposed.stream()
                .filter(entry -> GlossaryEdits.indexOf(screen.rows(), entry.id()) < 0)
                .forEach(screen.rows()::add);
    }

    private void reviewed(final GlossaryReviewReport report) {
        log.info(
                "model review removed {}, updated {} and suggested targets for {} entries",
                report.removed(),
                report.updated(),
                report.suggested());
        screen.rows().setAll(report.entries());
        line(
                GlossaryNotice.Level.INFO,
                screen.messages()
                        .get(MessageKey.NAMES_STYLE_REVIEWED, report.removed(), report.updated(), report.suggested()));
    }

    private <T> void start(
            final ActivityKind what, final MessageKey noModel, final Work<T> work, final Consumer<T> onOk) {
        final Optional<ModelSelection> selection = screen.settings().selection();
        if (selection.isEmpty()) {
            log.debug("the model {} was not started: no model is chosen", what);
            line(GlossaryNotice.Level.ERROR, screen.messages().get(noModel));
            return;
        }
        if (isRefusedBecauseOtherWorkRuns(what)) {
            return;
        }
        final String project = screen.project().get();
        log.info(
                "model {} of project {} started with provider {}",
                what,
                project,
                selection.get().providerId());
        final long mine = ++ticket;
        requests = 0;
        suggesting = null;
        handle = screen.activities().begin(what, this::stop);
        busy.set(true);
        screen.notice().set(null);
        running = calls.start(
                what.name(),
                () -> screen.models()
                        .create(selection.get())
                        .flatMap(model -> work.run(model, event -> Platform.runLater(() -> progress(mine, event)))),
                answer -> ended(mine, what, project, answer, onOk));
    }

    private boolean isRefusedBecauseOtherWorkRuns(final ActivityKind what) {
        final Optional<ActivityKind> conflict = screen.activities().conflictFor(what);
        if (conflict.isEmpty()) {
            return false;
        }
        log.debug("the model {} was not started: {} is running", what, conflict.get());
        line(
                GlossaryNotice.Level.ERROR,
                screen.messages()
                        .get(
                                MessageKey.ACTIVITY_BLOCKED,
                                screen.messages().get(conflict.get().label())));
        return true;
    }

    private <T> void ended(
            final long mine,
            final ActivityKind what,
            final String project,
            final Result<T> answer,
            final Consumer<T> onOk) {
        if (mine != ticket) {
            log.debug("the stopped model {} answered late; its answer is dropped", what);
            return;
        }
        running = null;
        endActivity();
        busy.set(false);
        final AppError failure = answer.error();
        log.info("model {} ended ok={} requests={}", what, failure == null, requests);
        if (!project.equals(screen.project().get()) || (failure != null && failure.code() == ErrorCode.cancelled)) {
            log.debug(
                    "the model {} answer is not shown: project {} now {}",
                    what,
                    project,
                    screen.project().get());
            return;
        }
        if (failure != null) {
            log.warn("the model {} failed with {}", what, failure.code());
            line(GlossaryNotice.Level.ERROR, failure.message());
            return;
        }
        screen.notice().set(null);
        onOk.accept(Objects.requireNonNull(answer.data(), "data"));
    }

    private void progress(final long mine, final JobEvent event) {
        if (mine != ticket || running == null) {
            return;
        }
        if (event instanceof BatchStarted batch) {
            suggesting = batch;
            log.debug("suggestion batch {} of {} is out", batch.batch(), batch.batches());
            line(GlossaryNotice.Level.INFO, suggestingLine(batch));
            return;
        }
        if (event instanceof ModelCallStarted started) {
            requestOut(started);
        }
    }

    private void requestOut(final ModelCallStarted started) {
        if (started.attempt() == 1) {
            requests++;
            final ActivityTracker.Handle registered = handle;
            if (registered != null) {
                registered.requests(requests);
            }
        }
        log.debug("model request {} attempt {} of {} is out", requests, started.attempt(), started.maxAttempts());
        final BatchStarted batch = suggesting;
        line(
                GlossaryNotice.Level.INFO,
                started.kind() == CallKind.SUGGEST_TARGETS && batch != null
                        ? suggestingLine(batch)
                        : screen.messages()
                                .get(
                                        MessageKey.NAMES_STYLE_MODEL_PROGRESS,
                                        requests,
                                        started.attempt(),
                                        started.maxAttempts()));
    }

    private String suggestingLine(final BatchStarted batch) {
        return screen.messages().get(MessageKey.NAMES_STYLE_SUGGESTING, batch.batch(), batch.batches());
    }

    private void endActivity() {
        final ActivityTracker.Handle registered = handle;
        handle = null;
        if (registered != null) {
            registered.end();
        }
    }

    private void line(final GlossaryNotice.Level level, final String text) {
        screen.notice().set(new GlossaryNotice(level, text));
    }
}

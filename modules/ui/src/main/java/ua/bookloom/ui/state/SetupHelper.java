package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javafx.application.Platform;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatModelFactory;
import ua.bookloom.api.llm.ModelCapabilities;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.pipeline.BriefSampleRead;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.SetupAssistant;
import ua.bookloom.ui.BackgroundExecutor;

/**
 * Runs one of the model's setup proposals — a file name, a style for the Book Brief — in the background with the model
 * the person chose, registered as model work so it never overlaps a translation, and hands the answer back on the FX
 * thread. It proposes only: what to do with the answer is the caller's.
 */
@Slf4j
@Singleton
public final class SetupHelper {

    /** Why a proposal did not start. */
    public enum Refusal {
        /** No provider and model are chosen yet. */
        NO_MODEL,
        /** Other model work is running, named by the kind. */
        BUSY,
        /** Long work runs in the background; a message offering "Show progress" has already said so. */
        HIDDEN_WORK
    }

    private final SetupAssistant assistant;
    private final ChatModelFactory models;
    private final SettingsViewModel settings;
    private final ExecutorService executor;
    private final ActivityTracker activities;
    private final ModelCapabilities capabilities;
    private final HiddenWork hiddenWork;

    /**
     * Creates the helper.
     *
     * @param assistant the port that asks the model
     * @param models where the chosen model is built
     * @param settings where the chosen provider and model are read
     * @param executor the daemon executor the call runs on, never the FX thread
     * @param activities the model work under way
     * @param capabilities where the chosen model's context length is detected
     * @param hiddenWork long work in the background, which refuses a proposal with its own message
     */
    @Inject
    public SetupHelper(
            final SetupAssistant assistant,
            final ChatModelFactory models,
            final SettingsViewModel settings,
            @BackgroundExecutor final ExecutorService executor,
            final ActivityTracker activities,
            final ModelCapabilities capabilities,
            final HiddenWork hiddenWork) {
        this.assistant = Objects.requireNonNull(assistant, "assistant");
        this.models = Objects.requireNonNull(models, "models");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.activities = Objects.requireNonNull(activities, "activities");
        this.capabilities = Objects.requireNonNull(capabilities, "capabilities");
        this.hiddenWork = Objects.requireNonNull(hiddenWork, "hiddenWork");
    }

    /**
     * Captures the chosen model now so its context length can be asked for later, off the FX thread: detection is a
     * catalogue request, and the selection is a UI property that only the FX thread may read.
     *
     * @return asks the provider for the captured model's context length; empty when no model is chosen, the provider
     *     says nothing or the request fails. Call it from the background work, never the FX thread.
     */
    public Supplier<Optional<Integer>> detection() {
        final Optional<ModelSelection> selection = settings.selection();
        log.debug(
                "context detection captured for {}",
                selection.map(ModelSelection::modelId).orElse("no model"));
        return () -> {
            final Optional<Integer> detected = selection.flatMap(capabilities::detectedTokens);
            log.debug("detected context of the chosen model: {}", detected);
            return detected;
        };
    }

    /**
     * The port the proposals are asked of, for the work a caller passes to {@link #run}.
     *
     * @return the assistant; never null
     */
    public SetupAssistant assistant() {
        return assistant;
    }

    /**
     * Starts one proposal. FX thread only.
     *
     * @param kind the model work it is registered as
     * @param work asks the assistant with the model it is given
     * @param done receives the answer on the FX thread, once
     * @return empty when it started, else why it did not, in which case {@code done} is never called
     */
    public <T> Optional<Refusal> run(
            final ActivityKind kind, final Function<ChatModel, Result<T>> work, final Consumer<Result<T>> done) {
        return runShown(kind, (model, progress) -> work.apply(model), done);
    }

    /**
     * Starts one proposal whose model calls and steps show in its progress dialog. FX thread only.
     *
     * @param kind the model work it is registered as
     * @param work asks the assistant with the model it is given and the receiver of each call's snapshot, which it may
     *     call from the background thread
     * @param done receives the answer on the FX thread, once
     * @return empty when it started, else why it did not, in which case {@code done} is never called
     */
    public <T> Optional<Refusal> runShown(
            final ActivityKind kind,
            final BiFunction<ChatModel, Consumer<JobEvent>, Result<T>> work,
            final Consumer<Result<T>> done) {
        return start(kind, work, false, (handle, answer, reads) -> {
            handle.end();
            log.info("setup proposal {} ended ok={}", kind, answer.isOk());
            done.accept(answer);
        });
    }

    /**
     * Starts one proposal whose answer needs the person's decision: when {@code offerOf} gives an offer, the activity
     * stays registered with it after the work has ended, so its dialog stays open until the offer's action, Cancel or
     * Esc; with no offer (a failure, a stop) it ends as any other. FX thread only.
     *
     * @param kind the model work it is registered as
     * @param work as for {@link #runShown}
     * @param offerOf receives the answer on the FX thread, once, and gives what the person may do with it
     * @return empty when it started, else why it did not, in which case {@code offerOf} is never called
     */
    public <T> Optional<Refusal> runForDecision(
            final ActivityKind kind,
            final BiFunction<ChatModel, Consumer<JobEvent>, Result<T>> work,
            final Function<Result<T>, Optional<ResultOffer>> offerOf) {
        return runForDecision(kind, work, offerOf, (answer, reads) -> List.of());
    }

    /**
     * As {@link #runForDecision(ActivityKind, BiFunction, Function)}, and the dialog's "What it decides" rows follow
     * the samples read while the work runs and become what {@code decided} says once the answer has arrived.
     *
     * @param decided the rows of the final answer, given the answer and what each sample read
     */
    public <T> Optional<Refusal> runForDecision(
            final ActivityKind kind,
            final BiFunction<ChatModel, Consumer<JobEvent>, Result<T>> work,
            final Function<Result<T>, Optional<ResultOffer>> offerOf,
            final BiFunction<Result<T>, List<BriefSampleRead>, List<DecisionRow>> decided) {
        return start(kind, work, true, (handle, answer, reads) -> {
            final Optional<ResultOffer> offer = offerOf.apply(answer);
            log.info("setup proposal {} ended ok={} offer={}", kind, answer.isOk(), offer.isPresent());
            if (offer.isEmpty()) {
                handle.end();
                return;
            }
            handle.decisions(decided.apply(answer, reads));
            handle.offer(offer.get());
        });
    }

    /**
     * The offer a proposal of {@code kind} holds for the person right now.
     *
     * @param kind the kind of work
     * @return its offer, with the actions that end the activity; empty when none is held
     */
    public Optional<ResultOffer> heldOffer(final ActivityKind kind) {
        return activities.running().stream()
                .filter(activity -> activity.kind() == kind)
                .map(activity -> activity.outcome().offer())
                .filter(Objects::nonNull)
                .findFirst();
    }

    /** What happens on the FX thread when the work's answer arrives. */
    @FunctionalInterface
    private interface Delivery<T> {
        void accept(ActivityTracker.Handle handle, Result<T> answer, List<BriefSampleRead> reads);
    }

    private <T> Optional<Refusal> start(
            final ActivityKind kind,
            final BiFunction<ChatModel, Consumer<JobEvent>, Result<T>> work,
            final boolean tallies,
            final Delivery<T> delivery) {
        final Optional<ModelSelection> selection = settings.selection();
        final Optional<Refusal> refusal = refusalFor(kind, selection);
        if (refusal.isPresent()) {
            return refusal;
        }
        log.info(
                "setup proposal {} started with provider {}",
                kind,
                selection.get().providerId());
        final InterruptibleWork running = new InterruptibleWork();
        final ActivityTracker.Handle handle = activities.begin(kind, running::stop);
        final List<BriefSampleRead> reads = new ArrayList<>();
        final CallFeed feed = new CallFeed(handle, activities::now, read -> {
            reads.add(read);
            if (tallies) {
                handle.decisions(BriefDecisions.running(reads));
            }
        });
        final Consumer<JobEvent> progress = event -> Platform.runLater(() -> feed.accept(event));
        submit(
                kind,
                handle,
                () -> running.run(() -> answer(selection.get(), work, progress), SetupHelper::stopped),
                answer -> delivery.accept(handle, answer, reads));
        return Optional.empty();
    }

    private Optional<Refusal> refusalFor(final ActivityKind kind, final Optional<ModelSelection> selection) {
        if (selection.isEmpty()) {
            log.debug("setup proposal {} not started: no model is chosen", kind);
            return Optional.of(Refusal.NO_MODEL);
        }
        if (hiddenWork.refuses(kind)) {
            log.debug("setup proposal {} not started: long work runs in the background", kind);
            return Optional.of(Refusal.HIDDEN_WORK);
        }
        if (activities.conflictFor(kind).isPresent()) {
            log.debug("setup proposal {} not started: {} is running", kind, activities.conflictFor(kind));
            return Optional.of(Refusal.BUSY);
        }
        return Optional.empty();
    }

    private <T> void submit(
            final ActivityKind kind,
            final ActivityTracker.Handle handle,
            final Supplier<Result<T>> job,
            final Consumer<Result<T>> done) {
        try {
            executor.execute(() -> {
                final Result<T> answer = job.get();
                Platform.runLater(() -> done.accept(answer));
            });
        } catch (RejectedExecutionException rejected) {
            log.error("the setup proposal {} could not be submitted", kind, rejected);
            handle.end();
            done.accept(Result.err(AppError.of(ErrorCode.internal, "Suggestion failed", "It could not be started.")));
        }
    }

    private static <T> Result<T> stopped() {
        return Result.err(AppError.of(ErrorCode.cancelled, "Suggestion stopped", "It was stopped before it finished."));
    }

    private <T> Result<T> answer(
            final ModelSelection selection,
            final BiFunction<ChatModel, Consumer<JobEvent>, Result<T>> work,
            final Consumer<JobEvent> progress) {
        try {
            return models.create(selection).flatMap(model -> work.apply(model, progress));
        } catch (Throwable thrown) {
            log.error("a setup proposal threw instead of returning a result", thrown);
            return Result.err(
                    AppError.of(ErrorCode.internal, "Suggestion failed", "An unexpected failure stopped it."));
        }
    }
}

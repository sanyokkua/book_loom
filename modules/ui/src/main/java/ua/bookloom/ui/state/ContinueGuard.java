package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import javafx.application.Platform;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ModelCatalog;
import ua.bookloom.api.llm.ModelInfo;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.pipeline.ProjectService;
import ua.bookloom.api.pipeline.ProjectSummary;
import ua.bookloom.api.project.RunRecord;
import ua.bookloom.ui.BackgroundExecutor;
import ua.bookloom.ui.Navigator;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.dialog.ContinuePrompt;

/**
 * What a continued run asks before it uses a model: first whether the chosen model is the one the project's latest
 * run was started with, then whether the model to use answers. "Answers" means its program lists its models, which
 * sends no prompt and costs no inference; a program that lists none counts as answering, one whose list lacks the
 * model does not.
 *
 * <p>"Try again" here asks only the one model again; it never looks for programs or saves anything, which is the
 * Projects screen's "Try again" ({@link LocalModelProbe}).
 */
@Slf4j
@Singleton
public final class ContinueGuard {

    private final ProjectService projects;
    private final CurrentProject current;
    private final ModelCatalog catalog;
    private final ContinuePrompt prompt;
    private final ExecutorService executor;
    private final Navigator navigator;

    /**
     * Receives the collaborators the injector owns.
     *
     * @param projects the service the stored latest run is read through, at the moment of each confirm
     * @param current the open project whose latest run is compared
     * @param catalog the port the answer test lists a model's program through
     * @param prompt the two questions
     * @param executor the daemon executor the stored run is read and the program asked on, never the FX thread
     * @param navigator where "Use another AI model…" leads
     */
    @Inject
    public ContinueGuard(
            final ProjectService projects,
            final CurrentProject current,
            final ModelCatalog catalog,
            final ContinuePrompt prompt,
            @BackgroundExecutor final ExecutorService executor,
            final Navigator navigator) {
        this.projects = Objects.requireNonNull(projects, "projects");
        this.current = Objects.requireNonNull(current, "current");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.prompt = Objects.requireNonNull(prompt, "prompt");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
    }

    /**
     * Asks what must be asked, then runs exactly one of the two callbacks. FX thread only.
     *
     * @param chosen the model chosen now
     * @param proceed what to do with the model to use: the chosen one, or the earlier one the person kept
     * @param cancelled what to do when nothing is to start; the caller clears its busy state here
     */
    public void confirm(final ModelSelection chosen, final Consumer<ModelSelection> proceed, final Runnable cancelled) {
        Objects.requireNonNull(chosen, "chosen");
        Objects.requireNonNull(proceed, "proceed");
        Objects.requireNonNull(cancelled, "cancelled");
        final OpenedBook book = current.book().get();
        final String projectId = book == null ? "" : book.projectId();
        log.debug("continue check for project {} with {}", projectId, chosen);
        executor.execute(() -> {
            final Optional<ModelSelection> stored = storedModel(projectId);
            onFx(() -> decide(chosen, stored, proceed, cancelled));
        });
    }

    private void decide(
            final ModelSelection chosen,
            final Optional<ModelSelection> stored,
            final Consumer<ModelSelection> proceed,
            final Runnable cancelled) {
        if (stored.isEmpty()) {
            log.info("continue: no stored model, current {}", chosen);
            proceed.accept(chosen);
        } else if (stored.get().equals(chosen)) {
            log.info("continue: stored model is the chosen one: {}", chosen);
            checkAnswer(chosen, proceed, cancelled);
        } else {
            log.info("continue: stored {} differs from current {}", stored.get(), chosen);
            prompt.askDifferent(
                    stored.get(),
                    chosen,
                    () -> checkAnswer(stored.get(), proceed, cancelled),
                    () -> checkAnswer(chosen, proceed, cancelled),
                    cancelled);
        }
    }

    private Optional<ModelSelection> storedModel(final String projectId) {
        final Result<List<ProjectSummary>> listed = projects.list();
        if (listed.isErr()) {
            log.warn(
                    "the stored model could not be read: {}",
                    Objects.requireNonNull(listed.error(), "error").code());
            return Optional.empty();
        }
        return Objects.requireNonNull(listed.data(), "data").stream()
                .filter(summary -> summary.project().id().equals(projectId))
                .findFirst()
                .map(ProjectSummary::latestRun)
                .map(RunRecord::model);
    }

    private void checkAnswer(
            final ModelSelection target, final Consumer<ModelSelection> proceed, final Runnable cancelled) {
        executor.execute(() -> {
            final boolean answering = answers(target);
            onFx(() -> {
                if (answering) {
                    proceed.accept(target);
                } else {
                    askNotAnswering(target, proceed, cancelled);
                }
            });
        });
    }

    private boolean answers(final ModelSelection target) {
        final Result<List<ModelInfo>> listed = catalog.listModels(target.providerId());
        if (listed.isErr()) {
            log.debug(
                    "answer check for {}: {}",
                    target.providerId(),
                    Objects.requireNonNull(listed.error(), "error").code());
            return false;
        }
        final List<ModelInfo> models = Objects.requireNonNull(listed.data(), "data");
        final boolean answering =
                models.isEmpty() || models.stream().anyMatch(info -> info.id().equals(target.modelId()));
        log.debug("answer check for {}: ok, model accepted {}", target.providerId(), answering);
        return answering;
    }

    private void askNotAnswering(
            final ModelSelection target, final Consumer<ModelSelection> proceed, final Runnable cancelled) {
        prompt.askNotAnswering(
                target,
                () -> {
                    log.debug("not-answering question: try again");
                    checkAnswer(target, proceed, cancelled);
                },
                () -> {
                    log.debug("not-answering question: use another AI model");
                    navigator.navigate(ViewNames.SETTINGS);
                    cancelled.run();
                },
                () -> {
                    log.debug("not-answering question: not now");
                    cancelled.run();
                });
    }

    private static void onFx(final Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }
}

package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import javafx.application.Platform;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.pipeline.ProjectService;
import ua.bookloom.api.pipeline.ProjectSummary;
import ua.bookloom.api.project.RunRecord;
import ua.bookloom.ui.BackgroundExecutor;
import ua.bookloom.ui.dialog.SwitchProviderPrompt;

/**
 * Makes the open provider the current one, asking first when the open book has translated segments, because the rest
 * of the book would then be translated by another model.
 */
@Slf4j
@Singleton
public final class ProviderSwitch {

    private final ProjectService projects;
    private final CurrentProject current;
    private final SettingsViewModel viewModel;
    private final ProviderForm form;
    private final SwitchProviderPrompt prompt;
    private final ExecutorService executor;
    private boolean counting;

    /**
     * Creates the switch.
     *
     * @param projects where the open book's decisions are counted
     * @param current the open book
     * @param viewModel the providers and the current choice
     * @param form the form whose provider becomes current
     * @param prompt the question
     * @param executor the daemon executor the service runs on, never the FX thread
     */
    @Inject
    public ProviderSwitch(
            final ProjectService projects,
            final CurrentProject current,
            final SettingsViewModel viewModel,
            final ProviderForm form,
            final SwitchProviderPrompt prompt,
            @BackgroundExecutor final ExecutorService executor) {
        this.projects = Objects.requireNonNull(projects, "projects");
        this.current = Objects.requireNonNull(current, "current");
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.form = Objects.requireNonNull(form, "form");
        this.prompt = Objects.requireNonNull(prompt, "prompt");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    /**
     * Makes the open provider and its model current. With no book open it happens at once; with a book open the
     * stored decisions are counted off the FX thread and, when there are any or they cannot be read, the person is
     * asked first. FX thread only.
     */
    public void use() {
        if (counting) {
            log.debug("use this one: already counting the decisions, press ignored");
            return;
        }
        final ModelSelection chosen = new ModelSelection(
                viewModel.editedProviderId().get(), form.draft().get().model().strip());
        final OpenedBook book = current.book().get();
        if (book == null) {
            log.debug("use this one: no book is open, switching at once");
            form.useThis(chosen.providerId(), chosen.modelId(), () -> {});
            return;
        }
        final String projectId = book.projectId();
        log.debug("use this one: counting the decisions of {}", projectId);
        counting = true;
        try {
            executor.execute(() -> {
                final Result<List<ProjectSummary>> listed = projects.list();
                Platform.runLater(() -> decide(projectId, chosen, listed));
            });
        } catch (RejectedExecutionException closing) {
            counting = false;
            log.warn("use this one: the window is closing, nothing was changed");
        }
    }

    private void decide(
            final String projectId, final ModelSelection chosen, final Result<List<ProjectSummary>> listed) {
        counting = false;
        final List<ProjectSummary> summaries = listed.data();
        final Optional<ProjectSummary> own = summaries == null
                ? Optional.empty()
                : summaries.stream()
                        .filter(summary -> summary.project().id().equals(projectId))
                        .findFirst();
        if (summaries != null && own.map(ProjectSummary::decidedSegments).orElse(0) == 0) {
            log.debug("use this one: nothing is decided in {}, switching without asking", projectId);
            form.useThis(chosen.providerId(), chosen.modelId(), () -> {});
            return;
        }
        log.debug("use this one: asking, decisions readable {}", summaries != null);
        final ModelSelection earlier = own.map(ProjectSummary::latestRun)
                .map(RunRecord::model)
                .orElseGet(() -> new ModelSelection(
                        viewModel.selectedProviderId().get(), viewModel.model().get()));
        prompt.ask(
                viewModel.providerName(chosen.providerId()),
                viewModel.providerName(earlier.providerId()),
                earlier.modelId(),
                () -> form.useThis(chosen.providerId(), chosen.modelId(), () -> {}));
    }
}

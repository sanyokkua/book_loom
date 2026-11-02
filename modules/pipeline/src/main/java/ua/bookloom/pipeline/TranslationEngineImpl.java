package ua.bookloom.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.pipeline.RunRequest;
import ua.bookloom.api.pipeline.TranslationEngine;
import ua.bookloom.api.pipeline.TranslationJob;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.run.RunStores;

/**
 * Creates the single-run job of a stored project. Nothing is checked here: a run that cannot start says so when it
 * runs, as the job's own result, before it calls the model.
 */
@Slf4j
public final class TranslationEngineImpl implements TranslationEngine {

    private final DocumentPort documents;
    private final ObjectMapper mapper;
    private final PromptTemplates templates;
    private final RunStores stores;
    private final RunChecks checks;
    private final RunPacing pacing;
    private final RunLogSources logSources;

    /**
     * Creates an engine with the application-wide tolerant JSON mapper, the stores every run reads and writes, the
     * checks every run decides and revises through, the pacing a run's recovery waits and ticks follow, and the
     * sources a run's log lines take their facts from.
     */
    @Inject
    public TranslationEngineImpl(
            final DocumentPort documents,
            final ObjectMapper mapper,
            final PromptTemplates templates,
            final RunStores stores,
            final RunChecks checks,
            final RunPacing pacing,
            final RunLogSources logSources) {
        this.documents = Objects.requireNonNull(documents, "documents");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.templates = Objects.requireNonNull(templates, "templates");
        this.stores = Objects.requireNonNull(stores, "stores");
        this.checks = Objects.requireNonNull(checks, "checks");
        this.pacing = Objects.requireNonNull(pacing, "pacing");
        this.logSources = Objects.requireNonNull(logSources, "logSources");
    }

    @Override
    public Result<TranslationJob> newJob(final RunRequest request, final ChatModel model) {
        try {
            Objects.requireNonNull(request, "request");
            Objects.requireNonNull(model, "model");
            log.debug("Preparing translation job project={} mode={}", request.projectId(), request.mode());
            return Result.ok(jobFor(request, model));
        } catch (Exception cause) {
            final AppError error = AppError.of(
                    ErrorCode.internal,
                    "This translation job could not be prepared",
                    "An unexpected error prevented this translation job from being prepared.",
                    null,
                    cause);
            log.error("Unexpected translation engine boundary failure code={}", error.code(), cause);
            return Result.err(error);
        }
    }

    private TranslationJobImpl jobFor(final RunRequest request, final ChatModel model) {
        return new TranslationJobImpl(
                request,
                new JobTools(documents, model, mapper, templates),
                stores,
                checks,
                pacing,
                logSources.factsFor(request));
    }
}

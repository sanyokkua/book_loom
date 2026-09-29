package ua.bookloom.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.time.Clock;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.SentenceSplitter;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.persistence.CheckpointPort;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.persistence.RunRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.persistence.TmRepository;
import ua.bookloom.api.pipeline.RunRequest;
import ua.bookloom.api.pipeline.TranslationEngine;
import ua.bookloom.api.pipeline.TranslationJob;
import ua.bookloom.pipeline.heal.QualityLoop;
import ua.bookloom.pipeline.project.OpenProjects;
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
    private final QualityLoop qualityLoop;
    private final SentenceSplitter splitter;
    private final Clock clock;

    /**
     * Creates an engine with the application-wide tolerant JSON mapper, the quality loop every run decides through,
     * and the sentence splitter an oversized segment is drafted in pieces with.
     */
    @Inject
    public TranslationEngineImpl(
            final DocumentPort documents,
            final ObjectMapper mapper,
            final PromptTemplates templates,
            final ProjectRepository projects,
            final SegmentRepository segments,
            final CheckpointPort checkpoint,
            final OpenProjects openProjects,
            final RunRepository runs,
            final GlossaryRepository glossary,
            final TmRepository tm,
            final QualityLoop qualityLoop,
            final SentenceSplitter splitter,
            final Clock clock) {
        this.documents = Objects.requireNonNull(documents, "documents");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.templates = Objects.requireNonNull(templates, "templates");
        this.stores = new RunStores(projects, segments, checkpoint, openProjects, runs, glossary, tm);
        this.qualityLoop = Objects.requireNonNull(qualityLoop, "qualityLoop");
        this.splitter = Objects.requireNonNull(splitter, "splitter");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Result<TranslationJob> newJob(final RunRequest request, final ChatModel model) {
        try {
            Objects.requireNonNull(request, "request");
            Objects.requireNonNull(model, "model");
            log.debug("Preparing translation job project={} mode={}", request.projectId(), request.mode());
            return Result.ok(new TranslationJobImpl(
                    documents, request, model, mapper, templates, stores, qualityLoop, splitter, clock));
        } catch (Throwable cause) {
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
}

package ua.bookloom.pipeline.glossary;

import com.google.inject.Inject;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.pipeline.GlossaryReviewReport;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.Project;
import ua.bookloom.pipeline.project.OpenProjects;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.StyleSheet;
import ua.bookloom.pipeline.run.JobModelCalls;

/**
 * The glossary's two model actions, the name scan and the review, sharing what both need: the open book, a brief
 * with a target language and a name policy for the suggested targets, and the run's model-call seam, so each call is announced, timed and attempt-counted exactly
 * as a run's calls are. A cancelled call (the calling thread interrupted) ends the action with nothing changed.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class GlossaryModelScans {

    private final ProjectRepository projects;
    private final OpenProjects openProjects;
    private final PreScan preScan;
    private final TermReview termReview;
    private final SuggestTargets suggestTargets;
    private final Clock clock;

    /** What a model action of the glossary runs on. */
    private record Book(List<Segment> segments, CallFrame frame, NamePolicy names) {}

    Result<List<GlossaryEntry>> prescan(
            final String projectId, final ChatModel model, final Consumer<JobEvent> progress) {
        return book(projectId)
                .flatMap(book -> preScan.scan(
                        projectId, book.segments(), book.frame(), book.names(), calls(model, progress, book)));
    }

    Result<GlossaryReviewReport> review(
            final String projectId, final ChatModel model, final Consumer<JobEvent> progress) {
        return book(projectId)
                .flatMap(book -> termReview.review(
                        projectId, book.segments(), book.frame(), book.names(), calls(model, progress, book)));
    }

    /**
     * Asks the model for a rendering of each entry by the call the glossary's suggestions use; nothing is written.
     *
     * @param projectId the non-null project whose book gives each term its example sentence
     * @param entries the non-null terms to render, as glossary entries of type term
     * @param model the non-null model to call
     * @param progress the non-null receiver of each call's start and finish
     * @return the entries with the suggested targets written in, or the first failed call's error
     */
    public Result<List<GlossaryEntry>> suggestOnto(
            final String projectId,
            final List<GlossaryEntry> entries,
            final ChatModel model,
            final Consumer<JobEvent> progress) {
        return book(projectId)
                .flatMap(book -> suggestTargets.suggestOnto(
                        entries, book.segments(), book.frame(), book.names(), calls(model, progress, book)));
    }

    private ModelCalls calls(final ChatModel model, final Consumer<JobEvent> progress, final Book book) {
        return new JobModelCalls(
                onSent -> {
                    onSent.run();
                    return model;
                },
                progress,
                clock,
                book.frame().targetLanguage());
    }

    private Result<Book> book(final String projectId) {
        final Result<Optional<Project>> found = projects.find(projectId);
        if (found.isErr()) {
            return Result.err(Objects.requireNonNull(found.error(), "error"));
        }
        final BookBrief brief = Objects.requireNonNull(found.data(), "found")
                .map(Project::brief)
                .orElse(null);
        final String target = brief == null ? null : brief.targetLanguage();
        if (brief == null || target == null || target.isBlank()) {
            log.warn("Glossary model action project={} refused: no target language chosen", projectId);
            return Result.err(AppError.of(
                    ErrorCode.validation, "Choose a target language", "Choose a target language before the scan."));
        }
        final Document document = openProjects.get(projectId);
        if (document == null) {
            log.warn("Glossary model action project={} refused: no book is open", projectId);
            return Result.err(
                    AppError.of(ErrorCode.validation, "No book is open", "Open the book before scanning for names."));
        }
        final String source = brief.sourceLanguage() == null ? document.declaredLang() : brief.sourceLanguage();
        log.debug("Glossary model action project={} source={} target={}", projectId, source, target);
        return Result.ok(new Book(
                GlossaryServiceImpl.bodySegments(document),
                new CallFrame(source, target, StyleSheet.from(brief), brief.foreignPassages()),
                brief.names()));
    }
}

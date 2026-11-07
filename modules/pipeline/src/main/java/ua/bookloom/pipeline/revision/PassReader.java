package ua.bookloom.pipeline.revision;

import static ua.bookloom.util.contract.ArgNames.ERROR;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.UnitRole;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.LexiconRepository;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.Project;
import ua.bookloom.pipeline.audit.FinalAudit;
import ua.bookloom.pipeline.checks.WordValidator;
import ua.bookloom.pipeline.heal.GateFunction;
import ua.bookloom.pipeline.project.OpenProjects;
import ua.bookloom.pipeline.project.SegmentLocators;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.typography.TypographyChoices;
import ua.bookloom.pipeline.typography.TypographyGate;

/**
 * Reads what a pass over the decided text needs before it changes anything — the stored brief, the opened book, its
 * document gate and the glossary — so the backward revision and the Proofread stage's code sweep read it one way.
 */
@Slf4j
@RequiredArgsConstructor
final class PassReader {

    private final DocumentPort documents;
    private final OpenProjects openProjects;
    private final ProjectRepository projects;
    private final GlossaryRepository glossary;
    private final LexiconRepository lexicon;

    /**
     * Reads the inputs of one pass over the opened book.
     *
     * @param projectId the non-null project id
     * @return the inputs; {@code validation} when the project is not stored, names no target language or its book is
     *     not open; or a storage error
     */
    Result<PassInputs> read(final String projectId) {
        return read(projectId, openProjects.get(projectId));
    }

    /**
     * Reads the inputs of one pass over a book the caller holds, which a run does even when its book was closed.
     *
     * @param projectId the non-null project id
     * @param document the book, or null when none is open
     * @return as {@link #read(String)}
     */
    Result<PassInputs> read(final String projectId, @Nullable final Document document) {
        final Result<Optional<Project>> found = projects.find(projectId);
        if (found.isErr()) {
            return Result.err(Objects.requireNonNull(found.error(), ERROR));
        }
        final BookBrief brief = Objects.requireNonNull(found.data(), "found")
                .map(Project::brief)
                .orElse(null);
        final String target = brief == null ? null : brief.targetLanguage();
        log.debug(
                "Pass inputs project={} stored={} bookOpen={} targetLanguage={}",
                projectId,
                brief != null,
                document != null,
                target);
        if (brief == null || document == null || target == null || target.isBlank()) {
            return Result.err(AppError.of(
                    ErrorCode.validation,
                    "Nothing to revise",
                    "The project must be stored, name a target language and have its book open to be revised."));
        }
        return lexicon.all(projectId)
                .map(BookContext::new)
                .flatMap(context ->
                        glossary.all(projectId).map(entries -> inputsOf(projectId, brief, document, entries, context)));
    }

    private PassInputs inputsOf(
            final String projectId,
            final BookBrief brief,
            final Document document,
            final List<GlossaryEntry> entries,
            final BookContext context) {
        final String target = Objects.requireNonNull(brief.targetLanguage(), "target");
        final Map<String, Segment> sources = new LinkedHashMap<>();
        document.units().forEach(unit -> unit.segments().forEach(segment -> sources.put(segment.id(), segment)));
        return new PassInputs(
                projectId,
                CallFrame.forBook(brief, document, openProjects.index(projectId)),
                brief.names(),
                TypographyGate.around(
                        GateFunction.of(documents, document.format()), target, TypographyChoices.of(brief)),
                sources,
                SegmentLocators.of(document),
                entries,
                List.copyOf(sources.keySet()),
                new FinalAudit.Book(brief, document, entries, WordValidator.none()),
                context,
                matterIds(document));
    }

    private static Set<String> matterIds(final Document document) {
        return document.units().stream()
                .filter(unit -> unit.isAuxiliary() || unit.role() != UnitRole.BODY)
                .flatMap(unit -> unit.segments().stream())
                .map(Segment::id)
                .collect(Collectors.toUnmodifiableSet());
    }
}

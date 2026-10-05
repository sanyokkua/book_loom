package ua.bookloom.pipeline.lexicon;

import com.google.inject.Inject;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.LexiconRepository;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.LexiconService;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.api.project.Project;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.glossary.FrequencyScan;
import ua.bookloom.pipeline.glossary.GlossaryIds;
import ua.bookloom.pipeline.glossary.GlossaryModelScans;
import ua.bookloom.pipeline.glossary.KeyTermScan;
import ua.bookloom.pipeline.project.OpenProjects;

/**
 * The one port the Recurring terms section of Names &amp; style edits the lexicon through: the deterministic scan, the
 * model's suggested renderings (the glossary's suggestion call over terms of type term), a hand-typed term, the
 * person's rendering, and promotion of an entry to the glossary, where the rendering stops being a hint and is applied
 * exactly. A term the glossary already holds is never in the lexicon, so the two never give a draft two renderings.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class LexiconServiceImpl implements LexiconService {

    private final LexiconRepository lexicon;
    private final GlossaryRepository glossary;
    private final ProjectRepository projects;
    private final OpenProjects openProjects;
    private final GlossaryModelScans modelScans;

    @Override
    public Result<List<LexiconEntry>> entries(final String projectId) {
        Objects.requireNonNull(projectId, "projectId");
        return guarded("entries", projectId, () -> lexicon.all(projectId));
    }

    @Override
    public Result<List<LexiconEntry>> scan(final String projectId) {
        Objects.requireNonNull(projectId, "projectId");
        return guarded("scan", projectId, () -> runScan(projectId));
    }

    @Override
    public Result<List<LexiconEntry>> suggest(
            final String projectId, final ChatModel model, final Consumer<JobEvent> progress) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(progress, "progress");
        return guarded("suggest", projectId, () -> runSuggest(projectId, model, progress));
    }

    @Override
    public Result<LexiconEntry> add(final String projectId, final String term) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(term, "term");
        return guarded("add", projectId, () -> addTerm(projectId, term.strip()));
    }

    @Override
    public Result<LexiconEntry> edit(final String projectId, final String term, @Nullable final String rendering) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(term, "term");
        return guarded(
                "edit",
                projectId,
                () -> held(projectId, term)
                        .flatMap(entry -> entry.map(found -> lexicon.put(found.withChosen(rendering)))
                                .orElseGet(() -> unknown(term))));
    }

    @Override
    public Result<GlossaryEntry> promote(final String projectId, final String term) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(term, "term");
        return guarded(
                "promote",
                projectId,
                () -> held(projectId, term)
                        .flatMap(entry -> entry.map(found -> promoteEntry(projectId, found))
                                .orElseGet(() -> unknown(term))));
    }

    @Override
    public Result<Boolean> remove(final String projectId, final String term) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(term, "term");
        return guarded("remove", projectId, () -> lexicon.remove(projectId, term));
    }

    private Result<List<LexiconEntry>> runScan(final String projectId) {
        final Document document = openProjects.get(projectId);
        if (document == null) {
            log.warn("Lexicon scan project={} refused: no book is open", projectId);
            return Result.err(
                    AppError.of(ErrorCode.validation, "No book is open", "Open the book before looking for terms."));
        }
        final Result<List<LexiconEntry>> proposed = KeyTermScan.newTerms(
                projectId, FrequencyScan.storyText(document), sourceLanguage(projectId, document), glossary, lexicon);
        if (proposed.isErr()) {
            return proposed;
        }
        for (final LexiconEntry entry : Objects.requireNonNull(proposed.data(), "proposed")) {
            final Result<LexiconEntry> stored = lexicon.put(entry);
            if (stored.isErr()) {
                return Result.err(Objects.requireNonNull(stored.error(), "error"));
            }
        }
        log.info("Lexicon scan project={} added={}", projectId, proposed.data().size());
        return lexicon.all(projectId);
    }

    private Result<List<LexiconEntry>> runSuggest(
            final String projectId, final ChatModel model, final Consumer<JobEvent> progress) {
        return lexicon.all(projectId).flatMap(all -> {
            final List<LexiconEntry> open =
                    all.stream().filter(entry -> entry.established().isEmpty()).toList();
            log.info("Lexicon suggestions project={} terms={} open={}", projectId, all.size(), open.size());
            if (open.isEmpty()) {
                return Result.ok(all);
            }
            return modelScans
                    .suggestOnto(projectId, asGlossary(projectId, open), model, progress)
                    .flatMap(answered -> storeSuggestions(projectId, open, answered));
        });
    }

    private Result<List<LexiconEntry>> storeSuggestions(
            final String projectId, final List<LexiconEntry> open, final List<GlossaryEntry> answered) {
        for (int i = 0; i < open.size(); i++) {
            final String target = answered.get(i).target();
            if (target != null && !target.isBlank()) {
                final Result<LexiconEntry> stored = lexicon.put(open.get(i).withSuggested(target));
                if (stored.isErr()) {
                    return Result.err(Objects.requireNonNull(stored.error(), "error"));
                }
            }
        }
        return lexicon.all(projectId);
    }

    private static List<GlossaryEntry> asGlossary(final String projectId, final List<LexiconEntry> entries) {
        return entries.stream()
                .map(entry -> new GlossaryEntry(
                        GlossaryIds.of(projectId, entry.term()),
                        projectId,
                        entry.term(),
                        null,
                        TermType.TERM,
                        Gender.UNKNOWN,
                        false))
                .toList();
    }

    private Result<LexiconEntry> addTerm(final String projectId, final String term) {
        if (term.isBlank()) {
            return Result.err(
                    AppError.of(ErrorCode.validation, "Empty term", "Type the word or title to keep consistent."));
        }
        final Result<Optional<GlossaryEntry>> inGlossary = glossary.findByTerm(projectId, term);
        if (inGlossary.isErr()) {
            return Result.err(Objects.requireNonNull(inGlossary.error(), "error"));
        }
        final Result<Optional<LexiconEntry>> inLexicon = held(projectId, term);
        if (inLexicon.isErr()) {
            return Result.err(Objects.requireNonNull(inLexicon.error(), "error"));
        }
        if (Objects.requireNonNull(inGlossary.data(), "glossary").isPresent()
                || Objects.requireNonNull(inLexicon.data(), "lexicon").isPresent()) {
            log.debug("Lexicon add refused project={}: the term is already held", projectId);
            return Result.err(AppError.of(
                    ErrorCode.validation, "Duplicate term", "'" + term + "' is already in the glossary or the list."));
        }
        return lexicon.put(LexiconEntry.of(projectId, term));
    }

    private Result<GlossaryEntry> promoteEntry(final String projectId, final LexiconEntry entry) {
        final String rendering = entry.established().orElse(null);
        final Result<GlossaryEntry> added = glossary.add(new GlossaryEntry(
                GlossaryIds.of(projectId, entry.term()),
                projectId,
                entry.term(),
                rendering,
                TermType.TERM,
                Gender.UNKNOWN,
                false));
        if (added.isErr()) {
            return added;
        }
        final Result<Boolean> removed = lexicon.remove(projectId, entry.term());
        log.info("Lexicon term promoted project={} hadRendering={}", projectId, rendering != null);
        return removed.isErr() ? Result.err(Objects.requireNonNull(removed.error(), "error")) : added;
    }

    private Result<Optional<LexiconEntry>> held(final String projectId, final String term) {
        final String key = LexiconEntry.keyOf(term);
        return lexicon.all(projectId)
                .map(all -> all.stream()
                        .filter(entry -> LexiconEntry.keyOf(entry.term()).equals(key))
                        .findFirst());
    }

    private @Nullable String sourceLanguage(final String projectId, final Document document) {
        final String chosen = Optional.ofNullable(projects.find(projectId).data())
                .flatMap(project -> project.map(Project::brief))
                .map(BookBrief::sourceLanguage)
                .orElse(null);
        return chosen == null ? document.declaredLang() : chosen;
    }

    private static <T> Result<T> unknown(final String term) {
        log.debug("Lexicon change refused: no such term");
        log.trace("Lexicon change refused: no entry for {}", term);
        return Result.err(AppError.of(ErrorCode.validation, "Unknown term", "'" + term + "' is not in the list."));
    }

    private static <T> Result<T> guarded(final String action, final String projectId, final Supplier<Result<T>> body) {
        log.debug("Lexicon {} project={}", action, projectId);
        try {
            return body.get();
        } catch (Throwable cause) {
            final AppError error = AppError.of(
                    ErrorCode.internal,
                    "Recurring terms failed",
                    "The recurring terms could not be updated.",
                    null,
                    cause);
            log.error(
                    "Unexpected lexicon failure action={} project={} code={}", action, projectId, error.code(), cause);
            return Result.err(error);
        }
    }
}

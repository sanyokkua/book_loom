package ua.bookloom.pipeline.setup;

import static ua.bookloom.util.contract.ArgNames.PROJECT_ID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.MetadataKey;
import ua.bookloom.api.llm.CallLedger;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.pipeline.BriefSuggestion;
import ua.bookloom.api.pipeline.FileNameSuggestion;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.SetupAssistant;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.NarratorHint;
import ua.bookloom.api.project.Project;
import ua.bookloom.pipeline.CallKindTotals;
import ua.bookloom.pipeline.PrepSummary;
import ua.bookloom.pipeline.context.ContextBuilder;
import ua.bookloom.pipeline.context.ModelWindows;
import ua.bookloom.pipeline.narrator.NarratorDetector;
import ua.bookloom.pipeline.project.OpenProjects;
import ua.bookloom.pipeline.project.OriginalTitles;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.OutputLimit;
import ua.bookloom.pipeline.prompt.PromptName;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.run.JobModelCalls;
import ua.bookloom.util.text.Edges;

/**
 * Asks the chosen model for the two things a person would otherwise type: the translated book's file name and the
 * tone and style of the Book Brief. The file name is one small call; the brief is a knowledge question, one reading of
 * each of a few single windows of the story and one synthesis, every field then decided once on a basis the code checks
 * ({@link BriefSuggester}). Every reply is read leniently, cleaned in code and returned as a proposal, never applied.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class SetupAssistantImpl implements SetupAssistant {

    private static final int NAME_TOKENS = 96;
    private static final String DOTS_AND_BLANKS = ". \t\n\u000B\f\r";
    // A file name holds at most 255 bytes on the common file systems; the suffix and a margin are kept free.
    private static final int MAX_NAME_BYTES = 200;
    private static final Pattern RESERVED = Pattern.compile("(?i)(con|prn|aux|nul|com[1-9]|lpt[1-9])(\\..*)?");
    private static final Pattern ILLEGAL = Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}\\p{Cf}]");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private final ProjectRepository projects;
    private final SegmentRepository segments;
    private final GlossaryRepository glossary;
    private final OpenProjects openProjects;
    private final ContextBuilder builder;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final CallLedger ledger;

    /**
     * A setup assistant whose calls are counted nowhere (tests, evals).
     */
    public SetupAssistantImpl(
            final ProjectRepository projects,
            final SegmentRepository segments,
            final GlossaryRepository glossary,
            final OpenProjects openProjects,
            final PromptTemplates templates,
            final ObjectMapper mapper,
            final Clock clock) {
        this(
                projects,
                segments,
                glossary,
                openProjects,
                new ContextBuilder(templates),
                mapper,
                clock,
                CallLedger.none());
    }

    @Override
    public Result<FileNameSuggestion> suggestFileName(final String projectId, final ChatModel model) {
        return suggestFileName(projectId, model, event -> {});
    }

    @Override
    public Result<FileNameSuggestion> suggestFileName(
            final String projectId, final ChatModel model, final Consumer<JobEvent> progress) {
        Objects.requireNonNull(projectId, PROJECT_ID);
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(progress, "progress");
        log.debug("File name suggestion requested project={}", projectId);
        try {
            return opened(projectId).flatMap(book -> askName(book, calls(model, progress, book)));
        } catch (Exception cause) {
            return failed("file name suggestion", cause);
        }
    }

    @Override
    public Result<BriefSuggestion> suggestBrief(final String projectId, final ChatModel model) {
        return suggestBrief(projectId, model, null, event -> {});
    }

    @Override
    public Result<BriefSuggestion> suggestBrief(
            final String projectId,
            final ChatModel model,
            final @Nullable Integer detectedContext,
            final Consumer<JobEvent> progress) {
        Objects.requireNonNull(projectId, PROJECT_ID);
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(progress, "progress");
        log.debug("Brief suggestion requested project={} detectedContext={}", projectId, detectedContext);
        final long mark = ledger.mark();
        try {
            final int window = ModelWindows.of(model, detectedContext);
            return opened(projectId)
                    .flatMap(book -> new BriefSuggester(setup(), new BookKnowledge(builder, mapper), glossary)
                            .suggest(book, window, calls(model, progress, book, window)));
        } catch (Exception cause) {
            return failed("brief suggestion", cause);
        } finally {
            PrepSummary.log("brief", projectId, CallKindTotals.of(ledger.since(mark)));
        }
    }

    @Override
    public Result<Narrator> recheckNarrator(final String projectId) {
        Objects.requireNonNull(projectId, PROJECT_ID);
        log.debug("Narrator re-check requested project={}", projectId);
        try {
            return opened(projectId).flatMap(book -> glossary.all(projectId).map(people -> rechecked(book, people)));
        } catch (Exception cause) {
            return failed("narrator re-check", cause);
        }
    }

    @Override
    public Result<NarratorHint> detectNarrator(final String projectId) {
        Objects.requireNonNull(projectId, PROJECT_ID);
        log.debug("Narrator detection requested project={}", projectId);
        try {
            return opened(projectId).map(SetupAssistantImpl::detected);
        } catch (Exception cause) {
            return failed("narrator detection", cause);
        }
    }

    private static NarratorHint detected(final SetupBook book) {
        final NarratorHint hint = NarratorDetector.detect(book.document(), book.languageOf())
                .orElse(new NarratorHint(NarratorHint.Person.THIRD, 0, List.of(), 0));
        log.info(
                "Narrator detected for the start check project={} language={} person={} judged={}",
                book.project().id(),
                book.languageOf(),
                hint.person(),
                hint.judgedChapters());
        return hint;
    }

    private static Narrator rechecked(final SetupBook book, final List<GlossaryEntry> people) {
        final Narrator held = book.project().brief().narrator();
        if (held.evidence().isEmpty()) {
            return held;
        }
        final BookSample whole = new BookSample(BookSamples.paragraphs(book.document()), 0, true, book.languageOf());
        return NarratorRecheck.of(held, people, whole, book.languageOf());
    }

    private ModelCalls calls(final ChatModel model, final Consumer<JobEvent> progress, final SetupBook book) {
        return calls(model, progress, book, ModelWindows.of(model, null));
    }

    private ModelCalls calls(
            final ChatModel model, final Consumer<JobEvent> progress, final SetupBook book, final int window) {
        return new JobModelCalls(
                onSent -> {
                    onSent.run();
                    return model;
                },
                progress,
                clock,
                book.target(),
                window);
    }

    private Result<SetupBook> opened(final String projectId) {
        final Result<Optional<Project>> found = projects.find(projectId);
        if (found.isErr()) {
            return Result.err(Objects.requireNonNull(found.error(), "error"));
        }
        final Project project = Objects.requireNonNull(found.data(), "found").orElse(null);
        final Document document = openProjects.get(projectId);
        final String target = project == null ? null : project.brief().targetLanguage();
        if (project == null || document == null || target == null || target.isBlank()) {
            return Result.err(AppError.of(
                    ErrorCode.validation,
                    "Nothing to suggest",
                    "The project must be stored, name a target language and have its book open."));
        }
        return Result.ok(new SetupBook(project, document, target, openProjects.index(projectId)));
    }

    private Result<FileNameSuggestion> askName(final SetupBook book, final ModelCalls calls) {
        final String stem = book.fileStem();
        final Map<String, String> user = new HashMap<>();
        user.put("fileName", stem);
        metadata(book.document(), MetadataKey.TITLE).ifPresent(title -> user.put("title", title));
        metadata(book.document(), MetadataKey.AUTHOR).ifPresent(author -> user.put("author", author));
        final FileNameTargets targets = FileNameTargets.of(
                segments,
                book.project().id(),
                OriginalTitles.of(book.document(), book.project()).segmentId());
        Optional.ofNullable(targets.title()).ifPresent(title -> user.put("titleTarget", title));
        Optional.ofNullable(targets.author()).ifPresent(author -> user.put("authorTarget", author));
        return askFileName(book, user, calls)
                .flatMap(name -> name.isEmpty()
                        ? Result.<FileNameSuggestion>err(AppError.of(
                                ErrorCode.validation, "No name", "The model did not suggest a usable name."))
                        : Result.ok(spelled(checkedAuthor(book, user, name, calls), targets)));
    }

    private static FileNameSuggestion spelled(final FileNameSuggestion suggestion, final FileNameTargets targets) {
        return new FileNameSuggestion(targets.spell(suggestion.name()), suggestion.authorKeptInSourceScript());
    }

    private Result<String> askFileName(final SetupBook book, final Map<String, String> user, final ModelCalls calls) {
        return ask(book, PromptName.FILE_NAME, user, NAME_TOKENS, calls).map(this::fileNameFrom);
    }

    // A non-Latin target with the author still in Latin letters is asked once more, naming the part; when the second
    // answer fails or keeps the Latin author too, the name is still offered, with the flag the screen can word.
    private FileNameSuggestion checkedAuthor(
            final SetupBook book, final Map<String, String> user, final String name, final ModelCalls calls) {
        if (!FileNameAuthor.isLeftInAnotherScript(name, book.target())) {
            return new FileNameSuggestion(name, false);
        }
        log.warn("The suggested file name keeps its author in another script than the target's; asking once more");
        final Map<String, String> again = new HashMap<>(user);
        again.put("correction", FileNameAuthor.correction(name));
        final String second = askFileName(book, again, calls).data();
        if (second == null || second.isEmpty()) {
            log.debug("The corrected file name is unusable; the first one is kept and flagged");
            return new FileNameSuggestion(name, true);
        }
        final boolean stillLeft = FileNameAuthor.isLeftInAnotherScript(second, book.target());
        log.debug("The corrected file name author still in another script: {}", stillLeft);
        return new FileNameSuggestion(second, stillLeft);
    }

    private Result<String> ask(
            final SetupBook book,
            final PromptName name,
            final Map<String, String> user,
            final int tokens,
            final ModelCalls calls) {
        final SetupCalls setup = setup();
        return setup.send(
                setup.requestOf(book, name, user, new OutputLimit(tokens / 2, tokens)),
                name,
                calls,
                SetupCalls.askedOf(user));
    }

    private SetupCalls setup() {
        return new SetupCalls(builder, mapper);
    }

    private String fileNameFrom(final String reply) {
        final JsonNode node = setup().json(reply);
        final String raw =
                node != null && node.hasNonNull("target") ? node.get("target").asText() : "";
        return cleanFileName(raw);
    }

    /**
     * Takes a file name proposal and removes what a file name cannot hold, so no model reply can name a path: path
     * separators and other reserved characters, control and invisible format characters, edge dots, a Windows device
     * name, and anything past 200 bytes of UTF-8, cut between whole characters.
     */
    static String cleanFileName(final String raw) {
        String name = ILLEGAL.matcher(raw).replaceAll(" ");
        name = SPACES.matcher(name).replaceAll(" ").strip();
        name = Edges.strip(name, DOTS_AND_BLANKS).strip();
        final StringBuilder kept = new StringBuilder();
        int bytes = 0;
        for (final int point : name.codePoints().toArray()) {
            final int size = new String(Character.toChars(point)).getBytes(StandardCharsets.UTF_8).length;
            if (bytes + size > MAX_NAME_BYTES) {
                break;
            }
            kept.appendCodePoint(point);
            bytes += size;
        }
        final String cut = kept.toString().strip();
        return RESERVED.matcher(cut).matches() ? cut + "_" : cut;
    }

    private static Optional<String> metadata(final Document document, final MetadataKey key) {
        return Optional.ofNullable(document.metadata().get(key.key()))
                .map(String::strip)
                .filter(s -> !s.isEmpty());
    }

    private static <T> Result<T> failed(final String what, final Throwable cause) {
        log.error("Unexpected failure in the {}", what, cause);
        return Result.err(AppError.of(
                ErrorCode.internal, "Suggestion failed", "An unexpected failure stopped the suggestion.", null, cause));
    }
}

package ua.bookloom.pipeline.setup;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.pipeline.BriefField;
import ua.bookloom.api.pipeline.BriefSuggestion;
import ua.bookloom.api.pipeline.FileNameSuggestion;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.SetupAssistant;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Project;
import ua.bookloom.pipeline.heal.SelfHealCalls;
import ua.bookloom.pipeline.project.OpenProjects;
import ua.bookloom.pipeline.prompt.CallDescriptor;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ChatRequests;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.OutputLimit;
import ua.bookloom.pipeline.prompt.PromptName;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;
import ua.bookloom.pipeline.run.JobModelCalls;

/**
 * Asks the chosen model for the two things a person would otherwise type: the translated book's file name and the
 * tone and style of the Book Brief. The file name is one small call; the brief is one call for each of two disjoint
 * samples of the book ({@link BookSamples}), each answer held to a quote the code finds in its sample, and a field is
 * kept only when both samples agree; the narrator's gender is kept only from a quote that shows it by a name the
 * glossary or the first-name list genders, or by a word of address ({@link NarratorGenderEvidence}). Every reply is read leniently, cleaned in code and returned as a proposal, never
 * applied.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class SetupAssistantImpl implements SetupAssistant {

    private static final String FB2_ZIP = ".fb2.zip";
    private static final int NAME_TOKENS = 96;
    // Six fields, each a value, a quote of up to fifteen words and a confidence, and the narrator's name.
    private static final int BRIEF_TOKENS = 512;
    private static final int SAMPLES = 2;
    private static final String FALLBACK_LANGUAGE = "en";
    // A file name holds at most 255 bytes on the common file systems; the suffix and a margin are kept free.
    private static final int MAX_NAME_BYTES = 200;
    private static final Pattern RESERVED = Pattern.compile("(?i)(con|prn|aux|nul|com[1-9]|lpt[1-9])(\\..*)?");
    private static final Pattern ILLEGAL = Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}\\p{Cf}]");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private final ProjectRepository projects;
    private final SegmentRepository segments;
    private final GlossaryRepository glossary;
    private final OpenProjects openProjects;
    private final PromptTemplates templates;
    private final ObjectMapper mapper;
    private final Clock clock;

    @Override
    public Result<FileNameSuggestion> suggestFileName(final String projectId, final ChatModel model) {
        return suggestFileName(projectId, model, event -> {});
    }

    @Override
    public Result<FileNameSuggestion> suggestFileName(
            final String projectId, final ChatModel model, final Consumer<JobEvent> progress) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(progress, "progress");
        log.debug("File name suggestion requested project={}", projectId);
        try {
            return opened(projectId).flatMap(book -> askName(book, calls(model, progress, book)));
        } catch (Throwable cause) {
            return failed("file name suggestion", cause);
        }
    }

    @Override
    public Result<BriefSuggestion> suggestBrief(final String projectId, final ChatModel model) {
        return suggestBrief(projectId, model, event -> {});
    }

    @Override
    public Result<BriefSuggestion> suggestBrief(
            final String projectId, final ChatModel model, final Consumer<JobEvent> progress) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(progress, "progress");
        log.debug("Brief suggestion requested project={}", projectId);
        try {
            return opened(projectId).flatMap(book -> askBrief(book, calls(model, progress, book)));
        } catch (Throwable cause) {
            return failed("brief suggestion", cause);
        }
    }

    private ModelCalls calls(final ChatModel model, final Consumer<JobEvent> progress, final Book book) {
        return new JobModelCalls(
                onSent -> {
                    onSent.run();
                    return model;
                },
                progress,
                clock,
                book.target());
    }

    private Result<Book> opened(final String projectId) {
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
        return Result.ok(new Book(project, document, target));
    }

    private Result<FileNameSuggestion> askName(final Book book, final ModelCalls calls) {
        final String stem = stemOf(book.project().source().getFileName().toString());
        final Map<String, String> user = new HashMap<>();
        user.put("fileName", stem);
        metadata(book.document(), MetadataKey.TITLE).ifPresent(title -> user.put("title", title));
        metadata(book.document(), MetadataKey.AUTHOR).ifPresent(author -> user.put("author", author));
        final FileNameTargets targets =
                FileNameTargets.of(segments, book.project().id());
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

    private Result<String> askFileName(final Book book, final Map<String, String> user, final ModelCalls calls) {
        return ask(book, PromptName.FILE_NAME, user, NAME_TOKENS, calls).map(this::fileNameFrom);
    }

    // A non-Latin target with the author still in Latin letters is asked once more, naming the part; when the second
    // answer fails or keeps the Latin author too, the name is still offered, with the flag the screen can word.
    private FileNameSuggestion checkedAuthor(
            final Book book, final Map<String, String> user, final String name, final ModelCalls calls) {
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

    private Result<BriefSuggestion> askBrief(final Book book, final ModelCalls calls) {
        final List<BookSample> samples = BookSamples.of(book.document(), languageOf(book));
        if (samples.isEmpty()) {
            return Result.err(AppError.of(
                    ErrorCode.validation, "Nothing to read", "The book has no body text to suggest a style from."));
        }
        return glossary.all(book.project().id())
                .flatMap(people ->
                        askSamples(book, samples, new NarratorGenderEvidence(people, languageOf(book)), calls));
    }

    private Result<BriefSuggestion> askSamples(
            final Book book,
            final List<BookSample> samples,
            final NarratorGenderEvidence genders,
            final ModelCalls calls) {
        final List<@Nullable Map<BriefField, BriefReplies.Answer>> answers = new ArrayList<>();
        for (int index = 0; index < samples.size(); index++) {
            final BookSample sample = samples.get(index);
            log.debug(
                    "Brief sample {} of {}: {} passages, dialogue {} %",
                    index + 1, samples.size(), sample.passages().size(), sample.dialoguePercent());
            final Result<String> reply =
                    ask(book, PromptName.BRIEF_SUGGESTION, briefInput(book, sample), BRIEF_TOKENS, calls);
            if (reply.isErr()) {
                return Result.err(Objects.requireNonNull(reply.error(), "error"));
            }
            final JsonNode node = json(Objects.requireNonNull(reply.data(), "reply"));
            answers.add(node == null ? null : BriefReplies.read(node, sample, genders));
        }
        if (answers.stream().allMatch(Objects::isNull)) {
            return Result.err(AppError.of(
                    ErrorCode.validation, "Unreadable answer", "The model's suggestion could not be read."));
        }
        return Result.ok(combined(book, answers, samples.size()));
    }

    private static BriefSuggestion combined(
            final Book book, final List<@Nullable Map<BriefField, BriefReplies.Answer>> answers, final int taken) {
        final BriefSuggestion read = BriefAgreement.combine(answers, SAMPLES);
        final BriefSuggestion aligned = read.alignedTo(book.project().brief().narrator());
        log.info(
                "Brief suggested from {} samples: kept fields {}, narratorGender={}, voiceDropped={}",
                taken,
                agreed(aligned),
                aligned.narratorGender(),
                !Objects.equals(aligned.voiceEra(), read.voiceEra()));
        return aligned;
    }

    private static Map<String, String> briefInput(final Book book, final BookSample sample) {
        final Map<String, String> user = new HashMap<>();
        user.put("sample", sample.text());
        user.put("dialogueShare", Integer.toString(sample.dialoguePercent()));
        metadata(book.document(), MetadataKey.TITLE).ifPresent(title -> user.put("title", title));
        metadata(book.document(), MetadataKey.AUTHOR).ifPresent(author -> user.put("author", author));
        return user;
    }

    private static List<BriefField> agreed(final BriefSuggestion suggestion) {
        return suggestion.evidence().entrySet().stream()
                .filter(entry -> entry.getValue().isKept())
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }

    private static String languageOf(final Book book) {
        final String source = book.project().brief().sourceLanguage();
        final String declared = book.document().declaredLang();
        return source != null ? source : declared != null && !declared.isBlank() ? declared : FALLBACK_LANGUAGE;
    }

    private Result<String> ask(
            final Book book,
            final PromptName name,
            final Map<String, String> user,
            final int tokens,
            final ModelCalls calls) {
        final BookBrief brief = book.project().brief();
        final CallFrame frame = new CallFrame(
                brief.sourceLanguage() == null ? book.document().declaredLang() : brief.sourceLanguage(),
                book.target(),
                StyleSheet.from(brief),
                brief.foreignPassages(),
                null,
                brief.narrator());
        final List<ChatMessage> messages = SelfHealCalls.messagesFor(templates, name, frame, user);
        final ChatRequest request = ChatRequests.build(name, messages, new OutputLimit(tokens / 2, tokens), false);
        log.debug("Setup call {} sent", name);
        final Result<ChatResponse> reply =
                calls.callAbout(name.callKind(), List.of(), request, CallDescriptor.whole(name));
        if (reply.isErr()) {
            return Result.err(Objects.requireNonNull(reply.error(), "error"));
        }
        final String content = Objects.requireNonNull(reply.data(), "reply").content();
        log.trace("Setup call {} answered {}", name, content);
        return Result.ok(content);
    }

    private String fileNameFrom(final String reply) {
        final JsonNode node = json(reply);
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
        name = name.replaceAll("^[.\\s]+|[.\\s]+$", "").strip();
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

    private @Nullable JsonNode json(final String reply) {
        final int from = reply.indexOf('{');
        final int to = reply.lastIndexOf('}');
        if (from < 0 || to <= from) {
            return null;
        }
        try {
            return mapper.readTree(reply.substring(from, to + 1));
        } catch (java.io.IOException unreadable) {
            log.debug("Setup reply is not JSON");
            return null;
        }
    }

    private static Optional<String> metadata(final Document document, final MetadataKey key) {
        return Optional.ofNullable(document.metadata().get(key.key()))
                .map(String::strip)
                .filter(s -> !s.isEmpty());
    }

    private static String stemOf(final String fileName) {
        if (fileName.toLowerCase(Locale.ROOT).endsWith(FB2_ZIP)) {
            return fileName.substring(0, fileName.length() - FB2_ZIP.length());
        }
        final int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private static <T> Result<T> failed(final String what, final Throwable cause) {
        log.error("Unexpected failure in the {}", what, cause);
        return Result.err(AppError.of(
                ErrorCode.internal, "Suggestion failed", "An unexpected failure stopped the suggestion.", null, cause));
    }

    private record Book(Project project, Document document, String target) {}
}

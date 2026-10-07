package ua.bookloom.pipeline.setup;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.pipeline.BriefSuggestion;
import ua.bookloom.api.pipeline.SetupAssistant;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.Project;
import ua.bookloom.api.project.Register;
import ua.bookloom.pipeline.DisplayText;
import ua.bookloom.pipeline.glossary.FrequencyScan;
import ua.bookloom.pipeline.heal.SelfHealCalls;
import ua.bookloom.pipeline.project.OpenProjects;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ChatRequests;
import ua.bookloom.pipeline.prompt.OutputLimit;
import ua.bookloom.pipeline.prompt.PromptName;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;

/**
 * Asks the chosen model for the two things a person would otherwise type: the translated book's file name and the
 * tone and style of the Book Brief. Each is one small call over the opened book; the reply is read leniently, cleaned
 * in code and returned as a proposal, never applied.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class SetupAssistantImpl implements SetupAssistant {

    private static final String FB2_ZIP = ".fb2.zip";
    private static final int OPENING_CHARS = 3500;
    private static final int OPENING_PARAGRAPHS = 14;
    private static final int MIN_PARAGRAPH_CHARS = 40;
    private static final int NAME_TOKENS = 96;
    private static final int BRIEF_TOKENS = 220;
    private static final int MAX_NAME_CHARS = 150;
    private static final Pattern ILLEGAL = Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private final ProjectRepository projects;
    private final OpenProjects openProjects;
    private final PromptTemplates templates;
    private final ObjectMapper mapper;

    @Override
    public Result<String> suggestFileName(final String projectId, final ChatModel model) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(model, "model");
        log.debug("File name suggestion requested project={}", projectId);
        try {
            return opened(projectId).flatMap(book -> askName(book, model));
        } catch (Throwable cause) {
            return failed("file name suggestion", cause);
        }
    }

    @Override
    public Result<BriefSuggestion> suggestBrief(final String projectId, final ChatModel model) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(model, "model");
        log.debug("Brief suggestion requested project={}", projectId);
        try {
            return opened(projectId).flatMap(book -> askBrief(book, model));
        } catch (Throwable cause) {
            return failed("brief suggestion", cause);
        }
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

    private Result<String> askName(final Book book, final ChatModel model) {
        final String stem = stemOf(book.project().source().getFileName().toString());
        final Map<String, String> user = new HashMap<>();
        user.put("fileName", stem);
        metadata(book.document(), MetadataKey.TITLE).ifPresent(title -> user.put("title", title));
        metadata(book.document(), MetadataKey.AUTHOR).ifPresent(author -> user.put("author", author));
        return ask(book, PromptName.FILE_NAME, user, NAME_TOKENS, model)
                .flatMap(reply -> Result.ok(fileNameFrom(reply)))
                .flatMap(name -> name.isEmpty()
                        ? Result.<String>err(AppError.of(
                                ErrorCode.validation, "No name", "The model did not suggest a usable name."))
                        : Result.ok(name));
    }

    private Result<BriefSuggestion> askBrief(final Book book, final ChatModel model) {
        final String opening = opening(book.document());
        if (opening.isBlank()) {
            return Result.err(AppError.of(
                    ErrorCode.validation, "Nothing to read", "The book has no body text to suggest a style from."));
        }
        final Map<String, String> user = new HashMap<>();
        user.put("opening", opening);
        metadata(book.document(), MetadataKey.TITLE).ifPresent(title -> user.put("title", title));
        metadata(book.document(), MetadataKey.AUTHOR).ifPresent(author -> user.put("author", author));
        return ask(book, PromptName.BRIEF_SUGGESTION, user, BRIEF_TOKENS, model).flatMap(this::briefFrom);
    }

    private Result<String> ask(
            final Book book,
            final PromptName name,
            final Map<String, String> user,
            final int tokens,
            final ChatModel model) {
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
        final Result<ChatResponse> reply = model.chat(request);
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

    /** Takes a file name proposal and removes what a file name cannot hold, so no model reply can name a path. */
    static String cleanFileName(final String raw) {
        String name = ILLEGAL.matcher(raw).replaceAll(" ");
        name = SPACES.matcher(name).replaceAll(" ").strip();
        name = name.replaceAll("^[.\\s]+|[.\\s]+$", "").strip();
        return name.length() > MAX_NAME_CHARS
                ? name.substring(0, MAX_NAME_CHARS).strip()
                : name;
    }

    private Result<BriefSuggestion> briefFrom(final String reply) {
        final JsonNode node = json(reply);
        if (node == null) {
            return Result.err(AppError.of(
                    ErrorCode.validation, "Unreadable answer", "The model's suggestion could not be read."));
        }
        return Result.ok(new BriefSuggestion(
                text(node, "genre"),
                register(text(node, "register")),
                text(node, "voice"),
                text(node, "audience"),
                narrator(text(node, "narrator")),
                narratorGender(text(node, "narratorGender"))));
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

    private static @Nullable String text(final JsonNode node, final String field) {
        final String value = node.hasNonNull(field) ? node.get(field).asText().strip() : "";
        return value.isEmpty() ? null : value;
    }

    private static Register register(@Nullable final String value) {
        return switch (value == null ? "" : value.toLowerCase(Locale.ROOT)) {
            case "formal", "literary" -> Register.FORMAL_LITERARY;
            case "casual" -> Register.CASUAL;
            default -> Register.NEUTRAL;
        };
    }

    private static NarratorPerson narrator(@Nullable final String value) {
        return switch (value == null ? "" : value.toLowerCase(Locale.ROOT)) {
            case "first" -> NarratorPerson.FIRST;
            case "third" -> NarratorPerson.THIRD;
            default -> NarratorPerson.UNSPECIFIED;
        };
    }

    private static Gender narratorGender(@Nullable final String value) {
        return switch (value == null ? "" : value.toLowerCase(Locale.ROOT)) {
            case "male" -> Gender.MALE;
            case "female" -> Gender.FEMALE;
            default -> Gender.UNKNOWN;
        };
    }

    private static Optional<String> metadata(final Document document, final MetadataKey key) {
        return Optional.ofNullable(document.metadata().get(key.key()))
                .map(String::strip)
                .filter(s -> !s.isEmpty());
    }

    private static String opening(final Document document) {
        final StringBuilder text = new StringBuilder();
        int taken = 0;
        for (final var segment : FrequencyScan.storyText(document)) {
            final String paragraph = DisplayText.of(segment.masked()).strip();
            if (paragraph.length() < MIN_PARAGRAPH_CHARS) {
                continue;
            }
            text.append(paragraph).append("\n\n");
            if (++taken >= OPENING_PARAGRAPHS || text.length() >= OPENING_CHARS) {
                break;
            }
        }
        return text.length() > OPENING_CHARS
                ? text.substring(0, OPENING_CHARS)
                : text.toString().strip();
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

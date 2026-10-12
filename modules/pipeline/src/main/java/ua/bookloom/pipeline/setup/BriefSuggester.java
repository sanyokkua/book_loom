package ua.bookloom.pipeline.setup;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.MetadataKey;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.pipeline.BriefField;
import ua.bookloom.api.pipeline.BriefSuggestion;
import ua.bookloom.api.pipeline.FieldEvidence;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.NarratorHint;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.Register;
import ua.bookloom.pipeline.chunk.TokenEstimator;
import ua.bookloom.pipeline.narrator.NarratorDetector;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.OutputLimit;
import ua.bookloom.pipeline.prompt.PromptName;
import ua.bookloom.pipeline.setup.BriefDecision.Decided;
import ua.bookloom.pipeline.setup.BriefReplies.Answer;

/**
 * The Book Brief suggestion of one book (15i.B4): what the model knows of the book ({@link BookKnowledge}), one reading
 * of each of K single windows of its story ({@link BookSamples}), each answer held to a quote the code finds in its
 * window, then one synthesis call over the facts, the accepted knowledge and the verified readings; every field is then
 * decided once ({@link BriefDecision}) and says what it stands on. A failed window call fails the suggestion; a failed
 * knowledge question or synthesis only leaves its fields uncertain.
 */
@Slf4j
@RequiredArgsConstructor
final class BriefSuggester {

    // Six fields, each a quote of up to fifteen words, a value and a confidence, the narrator's name and the language.
    private static final int WINDOW_TOKENS = 512;
    // Three fields of a basis and a value; the longest is a voice of twelve words.
    private static final OutputLimit SYNTHESIS_LIMIT = new OutputLimit(160, 320);
    private static final List<BriefField> SYNTHESIZED =
            List.of(BriefField.GENRE, BriefField.AUDIENCE, BriefField.VOICE);
    // The prompt's own text is English whatever the book's language.
    private static final String PROMPT_LANGUAGE = "en";

    private final SetupCalls setup;
    private final BookKnowledge knowledge;
    private final GlossaryRepository glossary;

    /** What the windows read: each window's verified answers and, under Auto, the language it names. */
    private record Readings(List<@Nullable Map<BriefField, Answer>> answers, List<@Nullable String> languages) {}

    /**
     * Suggests the brief's tone and style.
     *
     * @param book the book
     * @param detectedContext the model's context window in tokens, or null when unknown
     * @param calls the model-call seam
     * @return the suggestion held to the brief's narrator, or the first window call's error, or a validation error when
     *     the book has no story to read or no window's reply could be read
     */
    Result<BriefSuggestion> suggest(
            final SetupBook book, final @Nullable Integer detectedContext, final ModelCalls calls) {
        final QualityDial dial = book.project().brief().dial();
        final int windowTokens = BookSamples.windowTokens(dial, detectedContext, fixedTokens(book));
        final List<BookSample> windows =
                BookSamples.of(book.document(), book.languageOf(), BookSamples.windowCount(dial), windowTokens);
        if (windows.isEmpty()) {
            return Result.err(AppError.of(
                    ErrorCode.validation, "Nothing to read", "The book has no body text to suggest a style from."));
        }
        log.info(
                "Brief suggestion: {} windows of at most {} tokens, dial={}, source language {}",
                windows.size(),
                windowTokens,
                dial,
                book.project().brief().sourceLanguage() == null ? "auto" : "chosen");
        final Knowledge known = knowledge.ask(BookFacts.of(book.document()), book.fileStem(), book.frame(), calls);
        return glossary.all(book.project().id())
                .flatMap(people ->
                        read(book, windows, new NarratorGenderEvidence(people, book.languageOf()), known, calls));
    }

    // What each window's call spends besides the passage: its system message, its other user text and the reply.
    private int fixedTokens(final SetupBook book) {
        final ChatRequest bare = windowRequest(book, new BookSample(List.of(), 0));
        final int prompt = bare.messages().stream()
                .mapToInt(message -> TokenEstimator.estimate(message.content(), PROMPT_LANGUAGE))
                .sum();
        log.debug("Brief window call without its passage: {} prompt tokens, reply cap {}", prompt, WINDOW_TOKENS);
        return prompt + WINDOW_TOKENS;
    }

    private Result<BriefSuggestion> read(
            final SetupBook book,
            final List<BookSample> windows,
            final NarratorGenderEvidence genders,
            final Knowledge known,
            final ModelCalls calls) {
        final Result<Readings> readings = readWindows(book, windows, genders, calls);
        if (readings.isErr()) {
            return Result.err(Objects.requireNonNull(readings.error(), "error"));
        }
        final Readings read = Objects.requireNonNull(readings.data(), "readings");
        if (read.answers().stream().allMatch(Objects::isNull)) {
            return Result.err(AppError.of(
                    ErrorCode.validation, "Unreadable answer", "The model's suggestion could not be read."));
        }
        final JsonNode synthesis = synthesize(book, known, read.answers(), calls);
        return Result.ok(decided(book, read, synthesis, known));
    }

    private Result<Readings> readWindows(
            final SetupBook book,
            final List<BookSample> windows,
            final NarratorGenderEvidence genders,
            final ModelCalls calls) {
        final @Nullable String chosen = book.project().brief().sourceLanguage();
        final List<@Nullable Map<BriefField, Answer>> answers = new ArrayList<>();
        final List<@Nullable String> languages = new ArrayList<>();
        for (int index = 0; index < windows.size(); index++) {
            final BookSample window = windows.get(index);
            log.debug("Brief window {} of {}: dialogue {} %", index + 1, windows.size(), window.dialoguePercent());
            final Result<String> reply = setup.send(windowRequest(book, window), PromptName.BRIEF_SUGGESTION, calls);
            if (reply.isErr()) {
                return Result.err(Objects.requireNonNull(reply.error(), "error"));
            }
            final JsonNode node = setup.json(Objects.requireNonNull(reply.data(), "reply"));
            answers.add(node == null ? null : BriefReplies.read(node, window, genders));
            languages.add(
                    node == null ? null : BriefReplies.language(node, chosen).orElse(null));
        }
        return Result.ok(new Readings(answers, languages));
    }

    // The synthesis reply, or null when the call failed or its reply holds no object: its fields are then uncertain.
    private @Nullable JsonNode synthesize(
            final SetupBook book,
            final Knowledge known,
            final List<@Nullable Map<BriefField, Answer>> answers,
            final ModelCalls calls) {
        final Map<String, String> user = BriefSynthesisInput.of(BookFacts.of(book.document()), known, answers);
        final ChatRequest request = setup.requestOf(book, PromptName.BRIEF_SYNTHESIS, user, SYNTHESIS_LIMIT);
        final Result<String> reply = setup.send(request, PromptName.BRIEF_SYNTHESIS, calls);
        if (reply.isErr()) {
            log.warn("Brief synthesis failed; genre, audience and voice stay uncertain");
            return null;
        }
        final JsonNode node = setup.json(Objects.requireNonNull(reply.data(), "reply"));
        log.debug("Brief synthesis reply readable: {}", node != null);
        return node;
    }

    private static BriefSuggestion decided(
            final SetupBook book, final Readings read, final @Nullable JsonNode synthesis, final Knowledge known) {
        final List<@Nullable Map<BriefField, Answer>> answers = read.answers();
        final NarratorHint detected =
                NarratorDetector.detect(book.document(), book.languageOf()).orElse(null);
        final Decided<NarratorPerson> person = BriefDecision.narrator(detected, answers);
        final Map<BriefField, Decided<@Nullable String>> synthesized = new EnumMap<>(BriefField.class);
        SYNTHESIZED.forEach(
                field -> synthesized.put(field, BriefDecision.synthesized(field, synthesis, known, answers)));
        final String language = book.project().brief().sourceLanguage() == null
                ? BriefDecision.languageProposal(read.languages())
                : null;
        final BriefSuggestion suggestion = suggestion(
                BriefDecision.register(answers),
                person,
                BriefDecision.narratorGender(person.value(), answers),
                synthesized,
                language);
        return aligned(book, suggestion, answers.size());
    }

    private static BriefSuggestion suggestion(
            final Decided<Register> register,
            final Decided<NarratorPerson> person,
            final Decided<Gender> gender,
            final Map<BriefField, Decided<@Nullable String>> synthesized,
            final @Nullable String language) {
        final Map<BriefField, FieldEvidence> evidence = new EnumMap<>(BriefField.class);
        synthesized.forEach((field, decided) -> evidence.put(field, decided.evidence()));
        evidence.put(BriefField.REGISTER, register.evidence());
        evidence.put(BriefField.NARRATOR, person.evidence());
        // A gender belongs to a first-person narrator; a third-person book has no narrator character to give one.
        if (person.value() != NarratorPerson.THIRD) {
            evidence.put(BriefField.NARRATOR_GENDER, gender.evidence());
        }
        return new BriefSuggestion(
                valueOf(synthesized, BriefField.GENRE),
                register.value(),
                valueOf(synthesized, BriefField.VOICE),
                valueOf(synthesized, BriefField.AUDIENCE),
                person.value(),
                gender.value(),
                evidence,
                language);
    }

    private static @Nullable String valueOf(
            final Map<BriefField, Decided<@Nullable String>> synthesized, final BriefField field) {
        final Decided<@Nullable String> decided = synthesized.get(field);
        return decided == null ? null : decided.value();
    }

    private static BriefSuggestion aligned(final SetupBook book, final BriefSuggestion read, final int windows) {
        final BriefSuggestion aligned = read.alignedTo(book.project().brief().narrator());
        log.info(
                "Brief suggested from {} windows: kept fields {}, narrator={} {}, voiceDropped={}, languageProposal={}",
                windows,
                BriefCalls.kept(aligned),
                aligned.narrator(),
                aligned.narratorGender(),
                !Objects.equals(aligned.voiceEra(), read.voiceEra()),
                aligned.languageProposal());
        return aligned;
    }

    private ChatRequest windowRequest(final SetupBook book, final BookSample window) {
        final boolean autoSource = book.project().brief().sourceLanguage() == null;
        final ChatRequest request = setup.requestOf(
                book,
                PromptName.BRIEF_SUGGESTION,
                windowInput(book, window, autoSource),
                new OutputLimit(WINDOW_TOKENS / 2, WINDOW_TOKENS));
        return autoSource ? BriefCalls.withLanguageAsked(request) : request;
    }

    private static Map<String, String> windowInput(
            final SetupBook book, final BookSample window, final boolean autoSource) {
        final Map<String, String> user = new HashMap<>();
        user.put("sample", window.text());
        user.put("dialogueShare", Integer.toString(window.dialoguePercent()));
        if (autoSource) {
            user.put("askLanguage", "yes");
        }
        metadata(book, MetadataKey.TITLE).ifPresent(title -> user.put("title", title));
        metadata(book, MetadataKey.AUTHOR).ifPresent(author -> user.put("author", author));
        return user;
    }

    private static Optional<String> metadata(final SetupBook book, final MetadataKey key) {
        return Optional.ofNullable(book.document().metadata().get(key.key()))
                .map(String::strip)
                .filter(value -> !value.isEmpty());
    }
}

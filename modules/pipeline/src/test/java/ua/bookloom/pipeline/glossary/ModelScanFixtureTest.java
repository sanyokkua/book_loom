package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.EntryChanges;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.TermType;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.PersistenceModule;
import ua.bookloom.pipeline.glossary.SuggestionReplies.Suggestion;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;

/**
 * The model name scan on the fixture book (`modules/app/src/test/resources/fixtures/earth-gravity`): gemma4:e4b kept
 * chapter-title words, spelled-out numbers and language names ({@code Gravity Formula}, {@code Six}, {@code Latin}) as
 * glossary rows. Title lines never become candidates, numbers and languages are never a name alone, and the verdict
 * step drops what the proposal stage kept wrongly.
 */
class ModelScanFixtureTest {

    private static final SuggestTargets SUGGEST = new SuggestTargets(new PromptTemplates(), new ObjectMapper());
    private static final NamePolicy POLICY = NamePolicy.TRANSLITERATE;

    private static final Path FIXTURES = Path.of("../app/src/test/resources/fixtures/earth-gravity");
    private static final CallFrame FRAME =
            new CallFrame("en", "uk", StyleSheet.from(BookBrief.defaults("en")), ForeignPassagePolicy.KEEP);
    private static final List<String> NAMES = List.of(
            "Eleanor Vance", "Vance", "Nell", "Tomas", "Reyes", "Harrow Vale", "Meridian Survey Institute", "Amulet");
    private static final List<String> TITLE_WORDS = List.of(
            "Earth Gravity Works",
            "Gravity Formula",
            "Harrow Vale Expedition",
            "Six",
            "Seven",
            "Long Night",
            "Songs",
            "Falling Things",
            "Earth Gravity",
            "Practical Introduction",
            "Latin",
            "French",
            "Plumb Line");
    private static final Pattern CANDIDATE_LINE = Pattern.compile("(?m)^(.+?) — ");
    private static final Pattern REVIEW_LINE = Pattern.compile("(?m)^- (.+?) — \\d+×(?: — \"(.*?)\")?");

    private final Injector injector = Guice.createInjector(new DocumentModule(), new PersistenceModule());

    @ParameterizedTest
    @ValueSource(strings = {"earth-gravity.md", "earth-gravity.epub", "earth-gravity.fb2", "earth-gravity.txt"})
    void candidates_fixtureBook_holdTheNamesAndNoTitleWordNumberOrLanguage(final String file) {
        final List<String> terms = FrequencyScan.candidates(body(file), 1, "en").stream()
                .map(NameCandidate::term)
                .toList();

        assertThat(terms).containsAll(NAMES).doesNotContainAnyElementsOf(TITLE_WORDS);
    }

    // The proposal stage keeps every candidate, as a small model did; the verdict step names only the eight names.
    @Test
    void scan_modelKeepsEveryCandidate_verdictStepWritesOnlyTheNames() {
        final GlossaryRepository glossary = injector.getInstance(GlossaryRepository.class);
        final PreScan preScan = new PreScan(
                new PromptTemplates(),
                new ObjectMapper(),
                glossary,
                new TermReview(new PromptTemplates(), new ObjectMapper(), glossary, SUGGEST),
                SUGGEST);

        final Result<EntryChanges<GlossaryEntry>> added =
                preScan.scan("p1", body("earth-gravity.md"), FRAME, POLICY, keepsAllThenJudges(Set.copyOf(NAMES)));

        assertThat(Objects.requireNonNull(added.data()).added())
                .extracting(GlossaryEntry::term)
                .containsExactlyInAnyOrderElementsOf(NAMES);
    }

    // The manifest's names under each policy: Keep original leaves only the object to the model, the other two ask
    // about every name, each with a sentence of the book.
    @ParameterizedTest
    @CsvSource({"KEEP_ORIGINAL,1", "TRANSLITERATE,5", "TRANSLATE,5"})
    void suggest_manifestNames_asksAboutWhatThePolicyLeavesToTheModel(final NamePolicy policy, final int asked)
            throws IOException {
        final List<GlossaryEntry> names = manifestNames();
        final List<ChatRequest> requests = new ArrayList<>();

        final Result<List<Suggestion>> suggested =
                SUGGEST.suggest(names, body("earth-gravity.md"), FRAME, policy, (kind, segmentId, request) -> {
                    requests.add(request);
                    return Result.ok(new ChatResponse("{\"suggestions\":[]}", FinishReason.STOP));
                });

        assertThat(requests).hasSize(1);
        assertThat(lines(requests.getFirst())).hasSize(asked).allMatch(line -> line.contains(" — \""));
        assertThat(suggested.data()).hasSize(names.size() - asked);
    }

    /** The manifest's names as the glossary would hold them: without an article or a title, typed by their kind. */
    private static List<GlossaryEntry> manifestNames() throws IOException {
        final JsonNode names = new ObjectMapper()
                .readTree(FIXTURES.resolve("manifest.json").toFile())
                .path("names");
        return StreamSupport.stream(names.spliterator(), false)
                .map(name -> {
                    final String term = name.path("name").asText().replaceFirst("^(Dr\\. |The |the )", "");
                    return new GlossaryEntry(
                            "p1:" + term,
                            "p1",
                            term,
                            null,
                            typeOf(name.path("kind").asText()),
                            Gender.UNKNOWN,
                            false);
                })
                .toList();
    }

    private static TermType typeOf(final String kind) {
        return switch (kind) {
            case "person" -> TermType.CHARACTER;
            case "place" -> TermType.PLACE;
            case "object" -> TermType.TERM;
            default -> TermType.OTHER;
        };
    }

    private static List<String> lines(final ChatRequest request) {
        return request.messages()
                .getLast()
                .content()
                .lines()
                .filter(line -> line.startsWith("- "))
                .toList();
    }

    private List<Segment> body(final String file) {
        final Document document = Objects.requireNonNull(
                injector.getInstance(DocumentPort.class)
                        .open(FIXTURES.resolve(file))
                        .data(),
                file);
        return GlossaryServiceImpl.bodySegments(document);
    }

    private static ModelCalls keepsAllThenJudges(final Set<String> names) {
        return (kind, segmentId, request) -> Result.ok(new ChatResponse(
                kind == CallKind.PRESCAN ? proposeAll(request) : judge(request, names), FinishReason.STOP));
    }

    private static String proposeAll(final ChatRequest request) {
        return "{\"terms\":["
                + String.join(
                        ",",
                        CANDIDATE_LINE
                                .matcher(userMessage(request))
                                .results()
                                .map(match -> "{\"term\":\"" + match.group(1) + "\",\"type\":\"other\"}")
                                .toList())
                + "]}";
    }

    // The model quotes the first example sentence it was shown, without the quotes that would break the JSON.
    private static String quotedExample(final @Nullable String example) {
        return example == null ? "" : example.replace("\"", "").replace("\\", "");
    }

    private static String judge(final ChatRequest request, final Set<String> names) {
        return "{\"verdicts\":["
                + String.join(
                        ",",
                        REVIEW_LINE
                                .matcher(userMessage(request))
                                .results()
                                .map(match -> "{\"term\":\"" + match.group(1) + "\",\"verdict\":\""
                                        + (names.contains(match.group(1)) ? "name" : "not-a-name")
                                        + "\",\"evidence\":\""
                                        + (names.contains(match.group(1)) ? "" : quotedExample(match.group(2)))
                                        + "\"}")
                                .toList())
                + "]}";
    }

    private static String userMessage(final ChatRequest request) {
        return request.messages().getLast().content();
    }
}

package ua.bookloom.pipeline.proofread;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.CallSnapshotUpdated;
import ua.bookloom.api.pipeline.CallState;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.pipeline.WireMockProvider;
import ua.bookloom.pipeline.context.ContextBuilder;
import ua.bookloom.pipeline.context.TranslatePlanner;

/**
 * The proofread call at the HTTP seam, both dialects (design D15 "Call", "Reply"): one window per call with its
 * read-only neighbours, the drafts' system part, the target's instruction, and a reply read as unchanged or as the
 * changed focus items only. Invented ru→uk and en→ja lines.
 */
class ProofreadCallWireMockTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final String BEFORE = "ch02.md:13";
    private static final String FOCUS = "ch02.md:14";
    private static final String AFTER = "ch02.md:15";
    private static final String BEFORE_TARGET = "Тарн мовчав біля дверей.";
    private static final String FOCUS_TARGET = "Мирена кивнула й промовила, що ключ має Тарн.";
    private static final String AFTER_TARGET = "Івар сказав, що повернеться.";
    private static final String UNCHANGED = "{\"unchanged\":true}";

    @ParameterizedTest
    @EnumSource(ProviderKind.class)
    void proofread_oneMark_oneCallWithNeighbours(final ProviderKind kind) {
        try (WireMockProvider provider = new WireMockProvider(kind)) {
            provider.stubResponder(body -> UNCHANGED);

            read(provider, ProofreadFixtures.brief("ru", "uk"));

            assertThat(provider.chatBodies()).hasSize(1);
            final String user = user(provider.chatBodies().getFirst());
            assertThat(user)
                    .contains(
                            "<s id=\"" + FOCUS + "\">", "Current translation: " + FOCUS_TARGET, "Check: flagged: echo")
                    .contains(BEFORE_TARGET, AFTER_TARGET)
                    .doesNotContain("<s id=\"" + BEFORE + "\">", "<s id=\"" + AFTER + "\">");
        }
    }

    // The proofread temperature, the proofread schema's title on both dialects, and no thinking asked for.
    @ParameterizedTest
    @EnumSource(ProviderKind.class)
    void proofread_request_lowTemperatureProofreadSchemaNoThinking(final ProviderKind kind) {
        try (WireMockProvider provider = new WireMockProvider(kind)) {
            provider.stubResponder(body -> UNCHANGED);

            read(provider, ProofreadFixtures.brief("ru", "uk"));

            final String body = provider.chatBodies().getFirst();
            assertThat(body).contains("\"title\":\"proofread\"", "\"temperature\":0.1");
            assertThat(json(body).path("think").asBoolean(false)).isFalse();
        }
    }

    @ParameterizedTest
    @EnumSource(ProviderKind.class)
    void proofread_ruUk_systemEqualsDraftAndUkInstruction(final ProviderKind kind) {
        try (WireMockProvider provider = new WireMockProvider(kind)) {
            provider.stubResponder(body -> UNCHANGED);
            final BookBrief brief = ProofreadFixtures.brief("ru", "uk");

            final TranslatePlanner planner = read(provider, brief).planner();

            final String draftSystem = Objects.requireNonNull(new ContextBuilder(ProofreadFixtures.TEMPLATES)
                            .plan(
                                    CallKind.DRAFT,
                                    TranslatePlanner.focusOf(FOCUS, "Мирена кивнула."),
                                    ProofreadFixtures.map(List.of(), brief),
                                    planner.frame(),
                                    planner.profile())
                            .data())
                    .systemText();
            final JsonNode messages = json(provider.chatBodies().getFirst()).path("messages");
            assertThat(messages.path(0).path("content").asText()).isEqualTo(draftSystem);
            assertThat(messages.path(1).path("content").asText())
                    .contains("Ти коректор перекладу з російської на українську.");
        }
    }

    @ParameterizedTest
    @EnumSource(ProviderKind.class)
    void proofread_enJa_englishDefault(final ProviderKind kind) {
        try (WireMockProvider provider = new WireMockProvider(kind)) {
            provider.stubResponder(body -> UNCHANGED);

            read(provider, ProofreadFixtures.brief("en", "ja"));

            assertThat(user(provider.chatBodies().getFirst()))
                    .contains("You proofread a translation from English into Japanese.");
        }
    }

    @ParameterizedTest
    @EnumSource(ProviderKind.class)
    void reply_unchanged_noChange(final ProviderKind kind) {
        try (WireMockProvider provider = new WireMockProvider(kind)) {
            provider.stubResponder(body -> UNCHANGED);

            final ProofreadCall.Answer answer = answer(provider);

            assertThat(answer.readable()).isTrue();
            assertThat(answer.readIds()).containsExactly(FOCUS);
            assertThat(answer.changes()).isEmpty();
        }
    }

    // A neighbour's item is dropped, and so is an item that only repeats the current text.
    @ParameterizedTest
    @EnumSource(ProviderKind.class)
    void reply_neighbourItemAndEcho_dropped(final ProviderKind kind) {
        try (WireMockProvider provider = new WireMockProvider(kind)) {
            provider.stubResponder(body -> "{\"items\":[{\"id\":\"" + BEFORE + "\",\"target\":\"Інше.\"},"
                    + "{\"id\":\"" + FOCUS + "\",\"target\":\"" + FOCUS_TARGET + "\"}]}");

            final ProofreadCall.Answer answer = answer(provider);

            assertThat(answer.changes()).isEmpty();
        }
    }

    @ParameterizedTest
    @EnumSource(ProviderKind.class)
    void reply_changedFocusItem_readAsTheChange(final ProviderKind kind) {
        try (WireMockProvider provider = new WireMockProvider(kind)) {
            provider.stubResponder(body -> "{\"items\":[{\"id\":\"" + FOCUS + "\",\"target\":\"Мирена кивнула.\"}]}");

            final ProofreadCall.Answer answer = answer(provider);

            assertThat(answer.changes()).containsExactly(Map.entry(FOCUS, "Мирена кивнула."));
        }
    }

    // A proofread call is shown like a draft: All calls… lists it with its window's sources and current texts.
    @ParameterizedTest
    @EnumSource(ProviderKind.class)
    void proofread_call_announcedAsShownCall(final ProviderKind kind) {
        try (WireMockProvider provider = new WireMockProvider(kind)) {
            provider.stubResponder(body -> UNCHANGED);
            final List<JobEvent> events = new ArrayList<>();

            read(provider, ProofreadFixtures.brief("ru", "uk"), events::add);

            assertThat(events)
                    .filteredOn(CallSnapshotUpdated.class::isInstance)
                    .map(event -> ((CallSnapshotUpdated) event).snapshot())
                    .last()
                    .satisfies(snapshot -> {
                        assertThat(snapshot.kind()).isEqualTo(CallKind.PROOFREAD);
                        assertThat(snapshot.state()).isEqualTo(CallState.ANSWERED);
                        assertThat(snapshot.segments()).singleElement().satisfies(segment -> {
                            assertThat(segment.id()).isEqualTo(FOCUS);
                            assertThat(segment.currentTarget()).isEqualTo(FOCUS_TARGET);
                        });
                    });
        }
    }

    /** The planner a call was planned with and the answer it gave. */
    private record Called(TranslatePlanner planner, ProofreadCall.Answer answer) {}

    private static ProofreadCall.Answer answer(final WireMockProvider provider) {
        return read(provider, ProofreadFixtures.brief("ru", "uk")).answer();
    }

    private static Called read(final WireMockProvider provider, final BookBrief brief) {
        return read(provider, brief, event -> {});
    }

    private static Called read(
            final WireMockProvider provider, final BookBrief brief, final Consumer<JobEvent> events) {
        final Document document = ProofreadFixtures.chapter(
                "ch02.md",
                ProofreadFixtures.lines(
                        16,
                        "13",
                        "Тарн молчал у двери.",
                        "14",
                        "Мирена кивнула и сказала, что ключ у Тарна.",
                        "15",
                        "Ивар сказал, что вернётся."));
        final ChatModel model = provider.model(TIMEOUT, ignored -> {});
        final TranslatePlanner planner = ProofreadFixtures.planner(ProofreadFixtures.TEMPLATES, document, brief, model);
        final ProofreadWindow window = ProofreadWindows.of(
                        List.of(new Mark(FOCUS, Mark.Tier.MARKED, List.of("flagged: echo"))),
                        document.units().getFirst().segments().stream()
                                .map(Segment::id)
                                .toList(),
                        id -> true,
                        4)
                .getFirst();
        final Result<ProofreadCall.Answer> answer = new ProofreadCall(
                        planner, ProofreadFixtures.calls(model, events), MAPPER)
                .read(window, texts(document), ProofreadFixtures.map(List.of(), brief));
        assertThat(answer.isOk()).as("proofread call: %s", answer.error()).isTrue();
        return new Called(planner, Objects.requireNonNull(answer.data()));
    }

    private static ProofreadCall.Texts texts(final Document document) {
        final Map<String, String> sources = new HashMap<>();
        document.units().getFirst().segments().forEach(segment -> sources.put(segment.id(), segment.masked()));
        return new ProofreadCall.Texts(
                sources, Map.of(BEFORE, BEFORE_TARGET, FOCUS, FOCUS_TARGET, AFTER, AFTER_TARGET));
    }

    private static String user(final String body) {
        return json(body).path("messages").path(1).path("content").asText();
    }

    private static JsonNode json(final String body) {
        try {
            return MAPPER.readTree(body);
        } catch (JsonProcessingException cause) {
            throw new AssertionError("unreadable request body", cause);
        }
    }
}

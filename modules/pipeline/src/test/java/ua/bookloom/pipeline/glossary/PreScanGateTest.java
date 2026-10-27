package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.glossary.GlossaryServiceFixtures.PROJECT;
import static ua.bookloom.pipeline.glossary.GlossaryServiceFixtures.book;
import static ua.bookloom.pipeline.glossary.GlossaryServiceFixtures.project;
import static ua.bookloom.pipeline.glossary.GlossaryServiceFixtures.withTarget;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.pipeline.EntryChanges;
import ua.bookloom.api.pipeline.GlossaryService;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.InMemoryPersistenceModule;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.project.OpenProjects;

/**
 * The model name scan is a second opinion for a book the code read badly, so it runs only at Max or when the code
 * proposes fewer than ten names (15i.N3). "The code proposes" is what {@link FrequencyScan#newTerms} proposes, the names
 * written three times or more once the triage has dropped what is not a name; a name written once or twice is
 * not a proposal. The gate sits in the glossary service's {@code prescan}, which knows the brief's dial; a scan the gate
 * turns away makes no model call and answers an empty change.
 */
class PreScanGateTest {

    private static final String PRESCAN_REPLY = "{\"terms\":[]}";
    private static final int TIMES_WRITTEN = 3;

    private Injector injector;
    private GlossaryService service;

    @BeforeEach
    void setUp() {
        injector = Guice.createInjector(
                new DocumentModule(),
                new InMemoryPersistenceModule(),
                binder -> binder.bind(Clock.class).toInstance(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)));
        service = injector.getInstance(GlossaryServiceImpl.class);
    }

    // Names "Xaa", "Xab", ... each written `times` times mid-sentence, in a body of one-line paragraphs.
    private static List<String> names(final int count, final int times) {
        return IntStream.range(0, count)
                .boxed()
                .flatMap(index -> Stream.generate(
                                () -> "We met X" + (char) ('a' + index / 26) + (char) ('a' + index % 26) + " today.")
                        .limit(times))
                .toList();
    }

    private Result<EntryChanges<GlossaryEntry>> prescan(
            final QualityDial dial, final List<String> lines, final ScriptedChatModel model) {
        final BookBrief brief = withTarget(BookBrief.defaults("en"), "uk").withDial(dial);
        injector.getInstance(ProjectRepository.class).save(project(brief));
        injector.getInstance(OpenProjects.class).put(PROJECT, book(lines, "Title"));
        return service.prescan(PROJECT, model, event -> {});
    }

    private static long candidateLinesSent(final ScriptedChatModel model) {
        return model.requests().stream()
                .map(ChatRequest::messages)
                .map(messages -> messages.getLast().content())
                .flatMap(String::lines)
                .filter(line -> line.matches("X[a-z]{2} — We met .*"))
                .count();
    }

    // IF the dial is Fast or Balanced and the code proposes ten names or more, THEN the model scan makes no call; at
    // fewer than ten, and always at Max, it asks once (one batch of up to forty candidates).
    @ParameterizedTest(name = "{0} with {1} names written 3 times -> {2} call(s)")
    @CsvSource({
        "BALANCED, 12, 0",
        "BALANCED, 10, 0",
        "BALANCED, 9, 1",
        "BALANCED, 1, 1",
        "FAST, 12, 0",
        "FAST, 10, 0",
        "FAST, 9, 1",
        "MAX, 30, 1",
        "MAX, 12, 1",
        "MAX, 9, 1"
    })
    void prescan_codeProposals_makeTheCallOnlyAtMaxOrUnderTen(
            final QualityDial dial, final int proposals, final int calls) {
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(Result.ok(new ChatResponse(PRESCAN_REPLY, FinishReason.STOP)));

        final Result<EntryChanges<GlossaryEntry>> result = prescan(dial, names(proposals, TIMES_WRITTEN), model);

        assertThat(result.isOk()).isTrue();
        assertThat(model.requests()).hasSize(calls);
    }

    // IF the gate turns the scan away, THEN the answer is an empty change, not an error and not a half-written entry.
    @ParameterizedTest
    @CsvSource({"BALANCED", "FAST"})
    void prescan_turnedAwayByTheGate_answersAnEmptyChangeAndWritesNothing(final QualityDial dial) {
        final ScriptedChatModel model = new ScriptedChatModel();

        final Result<EntryChanges<GlossaryEntry>> result = prescan(dial, names(12, TIMES_WRITTEN), model);

        assertThat(Objects.requireNonNull(result.data()).isEmpty()).isTrue();
        assertThat(service.entries(PROJECT).data()).isEmpty();
    }

    // IF a book has many names written once or twice, THEN they are not proposals: ten of them do not turn the scan
    // away, and the model is shown all of them (a name written once is exactly what the second opinion is for).
    @ParameterizedTest
    @CsvSource({"BALANCED", "FAST"})
    void prescan_manyNamesWrittenOnlyOnce_arePutToTheModelAsCandidatesNotCountedAsProposals(final QualityDial dial) {
        final List<String> lines = new ArrayList<>(names(5, TIMES_WRITTEN));
        lines.addAll(names(25, 1).stream()
                .map(line -> line.replace("We met X", "We met Y"))
                .toList());
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(Result.ok(new ChatResponse(PRESCAN_REPLY, FinishReason.STOP)));

        prescan(dial, lines, model);

        assertThat(model.requests()).hasSize(1);
        assertThat(model.requests()
                        .getFirst()
                        .messages()
                        .getLast()
                        .content()
                        .lines()
                        .filter(line -> line.matches("[XY][a-z]{2} — We met .*"))
                        .count())
                .isEqualTo(30);
    }

    // IF the scan is allowed, THEN the model is shown every candidate, as before the gate existed.
    @ParameterizedTest
    @CsvSource({"MAX, 30", "BALANCED, 9"})
    void prescan_allowed_showsTheModelEveryCandidate(final QualityDial dial, final int proposals) {
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(Result.ok(new ChatResponse(PRESCAN_REPLY, FinishReason.STOP)));

        prescan(dial, names(proposals, TIMES_WRITTEN), model);

        assertThat(candidateLinesSent(model)).isEqualTo(proposals);
    }
}

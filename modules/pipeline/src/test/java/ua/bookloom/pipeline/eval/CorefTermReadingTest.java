package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.lang.reflect.AnnotatedElement;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariables;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.eval.CorefReport.Outcome;
import ua.bookloom.pipeline.glossary.TermReading;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;

/**
 * The coreference probe read by the term reading (15i.E2 with 15i.N4), offline: a {@link CorefReader} backed by
 * {@link TermReading} sends one window per call, and only the votes the reading keeps reach the scorer, so the wrong
 * vote rate is the one the gate reads. The {@code --suite coref} wiring runs the same reader against a real model.
 */
class CorefTermReadingTest {

    private static final CallFrame FRAME =
            new CallFrame("en", "uk", StyleSheet.from(BookBrief.defaults("en")), ForeignPassagePolicy.KEEP);

    private static Map<String, CorefWindow> byId() {
        return CorefCorpus.windows().stream().collect(Collectors.toMap(CorefWindow::id, Function.identity()));
    }

    private static Result<ChatResponse> vote(final String quote, final String gender) {
        return Result.ok(new ChatResponse(
                "{\"windows\":[{\"window\":1,\"quote\":\"" + quote + "\",\"usage\":\"name\",\"kind\":\"person\","
                        + "\"gender\":\"" + gender + "\"}],\"speaking\":false,\"brand\":false}",
                FinishReason.STOP));
    }

    private static CorefReader reader(final ScriptedChatModel model, final List<CallKind> kinds) {
        final ModelCalls calls = (kind, segmentId, request) -> {
            kinds.add(kind);
            return model.chat(request);
        };
        return new TermReadingCorefReader(new TermReading(new PromptTemplates(), new ObjectMapper()), FRAME, calls);
    }

    private static String user(final ChatRequest request) {
        return request.messages().stream()
                .filter(message -> message.role() == ChatRole.USER)
                .map(ChatMessage::content)
                .findFirst()
                .orElseThrow();
    }

    // plain-01: the target's own "She"; speech-01: a "she" inside quotes, which the reading drops; next-02: another
    // woman's "she" in the narration, a wrong vote the reading cannot catch; none-01: the model abstains.
    @Test
    void score_termReadingReader_scoresOnlyTheVotesTheReadingKeeps() {
        final Map<String, CorefWindow> windows = byId();
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(vote("She had walked", "female"))
                .answer(vote("she is already", "female"))
                .answer(vote("she waited", "female"))
                .answer(vote("none", "none"));
        final List<CallKind> kinds = new ArrayList<>();

        final CorefReport report = CorefScorer.score(
                List.of(
                        Objects.requireNonNull(windows.get("plain-01")),
                        Objects.requireNonNull(windows.get("speech-01")),
                        Objects.requireNonNull(windows.get("next-02")),
                        Objects.requireNonNull(windows.get("none-01"))),
                reader(model, kinds));

        assertThat(report.rows())
                .extracting(CorefReport.Row::outcome)
                .containsExactly(Outcome.RIGHT_REFERENT, Outcome.ABSTENTION, Outcome.WRONG_VOTE, Outcome.ABSTENTION);
        assertThat(report.wrongVoteRate()).isEqualTo(0.5);
        assertThat(kinds).containsOnly(CallKind.TERM_READING).hasSize(4);
    }

    // One window per call, the target marked where it first stands, the window text whole.
    @Test
    void read_oneCorefWindow_sendsItAsTheCallsOnlyWindowWithTheTargetMarked() {
        final CorefWindow plain = Objects.requireNonNull(byId().get("plain-02"));
        final ScriptedChatModel model = new ScriptedChatModel().answer(vote("none", "none"));

        reader(model, new ArrayList<>()).read(plain.target(), plain.text());

        assertThat(model.requests()).hasSize(1);
        assertThat(user(model.requests().getFirst()))
                .contains("[1] [[Tovrin]] read the letter twice.", "Then he went out into the rain.")
                .doesNotContain("[2]");
    }

    // A failed call is no vote: the window counts as an abstention and the probe goes on.
    @Test
    void read_callFails_isNoClaim() {
        final CorefWindow plain = Objects.requireNonNull(byId().get("plain-02"));
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(Result.err(ua.bookloom.api.AppError.of(
                        ua.bookloom.api.ErrorCode.unreachable, "Down", "The model is not running.")));

        assertThat(reader(model, new ArrayList<>()).read(plain.target(), plain.text()))
                .isEmpty();
    }

    // IF the coref suite were not listed where the default prompt eval steps aside, THEN `--suite coref` would run the
    // whole default eval too.
    @Test
    void promptEvalTest_corefSuite_stepsAsideForTheCorefSuite() {
        final DisabledIfEnvironmentVariable aside =
                PromptEvalTest.class.getAnnotation(DisabledIfEnvironmentVariable.class);

        assertThat("coref").matches(Objects.requireNonNull(aside).matches());
    }

    // The coref suite runs in its own promptEval class, gated on BOOKLOOM_EVAL_SUITE=coref and a model URL.
    @Test
    void corefEvalTest_class_isGatedOnTheCorefSuite() throws ClassNotFoundException {
        final AnnotatedElement suite = Class.forName("ua.bookloom.pipeline.eval.CorefEvalTest");
        final EnabledIfEnvironmentVariables gates = suite.getAnnotation(EnabledIfEnvironmentVariables.class);

        assertThat(Objects.requireNonNull(gates).value())
                .extracting(EnabledIfEnvironmentVariable::named, EnabledIfEnvironmentVariable::matches)
                .contains(
                        org.assertj.core.groups.Tuple.tuple("BOOKLOOM_EVAL_SUITE", "coref"),
                        org.assertj.core.groups.Tuple.tuple("BOOKLOOM_EVAL_URL", ".+"));
    }

    // scripts/eval-matrix.sh documents the suite, accepts it and prints its own table (right, wrong votes, abstentions,
    // the wrong-vote rate) instead of the default table.
    @Test
    void evalMatrix_script_knowsTheCorefSuite() throws IOException {
        final String script = Files.readString(Path.of("../../scripts/eval-matrix.sh"));

        assertThat(script).contains("|coref", "# --suite coref", "sys.argv[2] == \"coref\"");
    }
}

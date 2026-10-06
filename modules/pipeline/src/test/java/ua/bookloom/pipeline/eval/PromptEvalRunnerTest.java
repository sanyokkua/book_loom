package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.eval.EvalCase.Draft;
import ua.bookloom.pipeline.eval.EvalCase.Expect;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.run.JobModelCalls;

/** The runner against a scripted model: the requests it sends are the run's, so they can be read here. */
class PromptEvalRunnerTest {

    private static final String TARGET_REPLY = "{\"target\":\"Старий чоловік пішов до гавані.\"}";

    private record Sent(CallKind kind, ChatRequest request) {}

    private static ModelCalls recording(final List<Sent> sent) {
        return (kind, segmentId, request) -> {
            sent.add(new Sent(kind, request));
            return Result.ok(new ChatResponse(TARGET_REPLY, FinishReason.STOP));
        };
    }

    private static List<Sent> run(final EvalCase... cases) {
        final List<Sent> sent = new ArrayList<>();
        new PromptEvalRunner(recording(sent)).runAll(List.of(cases));
        return sent;
    }

    private static EvalCase named(final String name) {
        return PromptEvalCases.ALL.stream()
                .filter(evalCase -> evalCase.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static String user(final Sent sent) {
        return sent.request().messages().get(1).content();
    }

    @Test
    void runAll_glossaryNameCase_sendsTheRunsOwnGlossaryLine() {
        final List<Sent> sent = run(named("glossary-name"));

        assertThat(sent).singleElement().satisfies(call -> {
            assertThat(call.kind()).isEqualTo(CallKind.DRAFT);
            assertThat(user(call)).contains("Simon Lovelace → Саймон Лавлейс (character, male)");
        });
    }

    @Test
    void runAll_lockedNameCase_hidesTheNameBehindTheRunsTokenAndListsIt() {
        final List<Sent> sent = run(named("glossary-locked-name"));

        assertThat(user(sent.getFirst()))
                .contains("⟦g0⟧ → Бартімеус, character, male")
                .contains("<Text>\n⟦g0⟧ walked into the library")
                .doesNotContain("<Text>\nBartimaeus");
    }

    @Test
    void runAll_draftWithContext_showsEarlierPairsSummaryRecurringTermsAndNarrator() {
        final Draft draft = new Draft(
                "ctx",
                "I told the master the door was open.",
                List.of(),
                Expect.translate(),
                new EvalContext(
                        List.of(new EvalContext.Pair("He knocked.", "Він постукав.")),
                        "Раніше: хлопчик чекав.",
                        List.of(new EvalContext.Rendering("master", "господар")),
                        new Narrator(NarratorPerson.FIRST, Gender.MALE)));

        final Sent sent = run(draft).getFirst();

        assertThat(user(sent)).contains("Він постукав.", "Раніше: хлопчик чекав.", "master → господар");
        assertThat(sent.request().messages().getFirst().content()).contains("Narrator: first person, male");
    }

    @Test
    void runAll_placeholderRepairCase_sendsAPlaceholderRepairCall() {
        final List<Sent> sent = run(named("repair-placeholder"));

        assertThat(sent).singleElement().satisfies(call -> {
            assertThat(call.kind()).isEqualTo(CallKind.PLACEHOLDER_REPAIR);
            assertThat(user(call)).contains("Вона закрила важку книгу і зітхнула.");
        });
    }

    @Test
    void runAll_structuralRepairCase_sendsAStructuralRepairCall() {
        final List<Sent> sent = run(named("repair-structural"));

        assertThat(sent).singleElement().satisfies(call -> {
            assertThat(call.kind()).isEqualTo(CallKind.STRUCTURAL_REPAIR);
            assertThat(user(call)).contains("{\"translation\": \"Він відчинив старі двері.\"}");
        });
    }

    @Test
    void runAll_draftCallThroughTheRunsSeam_isSizedToTheEvalWindow() {
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(Result.ok(new ChatResponse(TARGET_REPLY, FinishReason.STOP)));
        final JobModelCalls calls =
                new JobModelCalls(onSent -> model, event -> {}, Clock.systemUTC(), "uk", EvalProject.window());

        new PromptEvalRunner(calls).runAll(List.of(named("plain-short")));

        assertThat(model.requests()).singleElement().satisfies(request -> {
            assertThat(request.contextWindow()).isEqualTo(EvalProject.window());
            assertThat(request.maxOutputTokens()).isLessThanOrEqualTo(EvalProject.window() / 2);
        });
    }
}

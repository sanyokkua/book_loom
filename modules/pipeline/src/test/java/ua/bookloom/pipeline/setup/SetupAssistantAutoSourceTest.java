package ua.bookloom.pipeline.setup;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.BriefSuggestion;
import ua.bookloom.api.project.Project;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.review.ReviewFixtures;
import ua.bookloom.pipeline.review.ReviewFixtures.Desk;

/** The brief suggestion of a book whose source language is Auto: the model is told to infer it, whatever the book says. */
class SetupAssistantAutoSourceTest {

    private static final String BODY = "The rain had not stopped for three days, and I was beginning to think the city "
            + "had forgotten the sun.\n\n"
            + "I lit a cigarette and waited for the phone to ring, but it never did that night or the next one.\n\n"
            + "Nobody came to the door, and the street stayed empty until the lamps went out at dawn.\n";
    private static final String REPLY = "{" + field("genre", "noir") + "," + field("register", "casual") + ","
            + field("voice", "terse") + "," + field("audience", "") + "," + field("narrator", "first") + ","
            + field("narratorGender", "unknown") + "}";

    private static String field(final String name, final String value) {
        return "\"" + name + "\":{\"value\":\"" + value + "\",\"evidence\":\"\",\"confidence\":0.5}";
    }

    @TempDir
    private Path tempDir;

    private ScriptedChatModel suggestUnderAuto(final String bookText) {
        final Desk desk = ReviewFixtures.markdown(tempDir, bookText);
        final Project stored = desk.projects().find(desk.projectId()).data().orElseThrow();
        desk.projects().save(stored.withBrief(stored.brief().withLanguages(null, "uk")));
        final ScriptedChatModel model = new ScriptedChatModel();
        model.answer(Result.ok(new ChatResponse(REPLY, FinishReason.STOP)))
                .answer(Result.ok(new ChatResponse(REPLY, FinishReason.STOP)));
        final SetupAssistantImpl assistant = new SetupAssistantImpl(
                desk.projects(),
                desk.segments(),
                desk.glossary(),
                desk.openProjects(),
                new PromptTemplates(),
                new ObjectMapper(),
                Clock.systemUTC());

        final Result<BriefSuggestion> suggestion = assistant.suggestBrief(desk.projectId(), model);

        assertThat(suggestion.isOk()).isTrue();
        return model;
    }

    // IF the setup call named a language nobody chose, THEN the model would be told to translate from it.
    @Test
    void suggestBrief_autoSourceBookDeclaringNothing_tellsTheModelToInferTheLanguage() {
        final ScriptedChatModel model = suggestUnderAuto(BODY);

        assertThat(model.requests())
                .isNotEmpty()
                .allSatisfy(request -> assertThat(request.messages().getFirst().content())
                        .contains("the language of this segment (infer it from its text)"));
    }

    // IF the book's own declaration reached the prompt under Auto, THEN Auto would silently mean "what the file says".
    @Test
    void suggestBrief_autoSourceBookDeclaringFrench_stillTellsTheModelToInferTheLanguage() {
        final ScriptedChatModel model = suggestUnderAuto("---\nlang: fr\n---\n\n" + BODY);

        assertThat(model.requests())
                .isNotEmpty()
                .allSatisfy(request -> assertThat(request.messages().getFirst().content())
                        .contains("the language of this segment (infer it from its text)")
                        .doesNotContain("French (fr)"));
    }

    // IF the passage's language were not asked, THEN Auto would have nothing to propose from (15i.B4 counts the
    // windows).
    @Test
    void suggestBrief_autoSource_asksEachWindowForTheLanguageItIsWrittenIn() {
        final ScriptedChatModel model = suggestUnderAuto(BODY);

        assertThat(model.requests())
                .isNotEmpty()
                .allSatisfy(request -> assertThat(request.messages())
                        .anyMatch(message -> message.content().contains("\"language\"")));
    }
}

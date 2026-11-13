package ua.bookloom.pipeline.setup;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.BriefField;
import ua.bookloom.api.pipeline.BriefSuggestion;
import ua.bookloom.api.pipeline.FieldEvidence;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.Project;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.review.ReviewFixtures;
import ua.bookloom.pipeline.review.ReviewFixtures.Desk;

/**
 * Prepare reads a first-person narrator's gender from the source's own past forms in narration: three forms of which
 * 80 % agree store it as verified with the quotes; fewer or mixed forms leave the model's answer. The book is invented.
 */
class NarratorFromSourceTest {

    private static final String WINDOW_QUOTE = "Я долго стояла у окна";

    @TempDir
    private Path tempDir;

    private BriefSuggestion suggested(final String book) {
        final Desk desk = ReviewFixtures.markdown(tempDir, book);
        final Project stored = Objects.requireNonNull(
                        desk.projects().find(desk.projectId()).data())
                .orElseThrow();
        desk.projects().save(stored.withBrief(stored.brief().withLanguages("ru", "uk")));
        final ScriptedChatModel model = new ScriptedChatModel();
        model.answerTo("brief-suggestion", window());
        model.answerTo("brief-synthesis", Result.ok(new ChatResponse("{}", FinishReason.STOP)));
        final SetupAssistantImpl assistant = new SetupAssistantImpl(
                desk.projects(),
                desk.segments(),
                desk.glossary(),
                desk.openProjects(),
                new PromptTemplates(),
                new ObjectMapper(),
                Clock.systemUTC());
        return Objects.requireNonNull(
                assistant.suggestBrief(desk.projectId(), model).data());
    }

    // The model says first person and gives no gender, so only the source's count can set it.
    private static Result<ChatResponse> window() {
        final String json = "{\"narrator\":{\"value\":\"first\",\"evidence\":\"" + WINDOW_QUOTE
                + "\",\"confidence\":0.9},\"narratorGender\":{\"value\":\"unknown\",\"evidence\":\"\",\"name\":\"\","
                + "\"confidence\":0.5}}";
        return Result.ok(new ChatResponse(json, FinishReason.STOP));
    }

    // Three feminine forms of the narrator in narration, and a masculine one inside quoted speech, give a verified
    // female narrator with the quotes.
    @Test
    void prepare_firstPersonFemalePastForms_storesVerifiedWithQuote() {
        final BriefSuggestion suggestion = suggested("Я вернулась домой поздно. " + WINDOW_QUOTE + ".\n\n"
                + "Потом я легла, но так и не уснула.\n\n«Я пришёл», сказал гость.\n");

        assertThat(suggestion.suggestedNarrator())
                .isEqualTo(new Narrator(
                        NarratorPerson.FIRST,
                        Gender.FEMALE,
                        Narrator.GenderOrigin.VERIFIED,
                        List.of("Я вернулась", "Я долго стояла", "я легла")));
        assertThat(suggestion.evidenceOf(BriefField.NARRATOR_GENDER))
                .hasValueSatisfying(proof -> assertThat(proof)
                        .extracting(FieldEvidence::basis, FieldEvidence::agreeing, FieldEvidence::samples)
                        .containsExactly(FieldEvidence.Basis.MEASURED, 3, 3));
    }

    // Three forms of which two are feminine are under the bar: the gender is not stored from the book.
    @Test
    void prepare_threeFormsTwoFeminine_isNotVerified() {
        final BriefSuggestion suggestion =
                suggested("Я вернулась домой поздно. " + WINDOW_QUOTE + ".\n\n" + "Потом я лёг, но так и не уснул.\n");

        assertThat(suggestion.narratorGender()).isEqualTo(Gender.UNKNOWN);
        assertThat(suggestion.suggestedNarrator().genderOrigin()).isNotEqualTo(Narrator.GenderOrigin.VERIFIED);
    }
}

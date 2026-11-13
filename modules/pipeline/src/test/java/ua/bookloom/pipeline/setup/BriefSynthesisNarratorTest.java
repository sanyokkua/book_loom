package ua.bookloom.pipeline.setup;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.BriefField;
import ua.bookloom.api.pipeline.BriefSuggestion;
import ua.bookloom.api.pipeline.FieldEvidence;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.review.ReviewFixtures;
import ua.bookloom.pipeline.review.ReviewFixtures.Desk;

/**
 * The synthesis always gives its most probable narrator gender (book-brief "Keep the narrator's most probable gender
 * as likely and verify it only by evidence the code checks"): kept as likely with no quote when the code cannot check
 * it, and the book's, with its quote, when the quote names a known person.
 */
class BriefSynthesisNarratorTest {

    private static final String VOCATIVE = "Hold on tight, Ray";
    private static final String BOOK = "\"" + VOCATIVE + ",\" Danny said, and I laughed at him for a while.\n\n"
            + "The rain had not stopped for three days, and I was beginning to think the city had forgotten the sun.\n\n"
            + "I lit a cigarette and waited for the phone to ring, but it never did that night or the next one.\n";
    private static final String WINDOW = "{\"narrator\":{\"value\":\"first\",\"evidence\":\"I lit a cigarette\","
            + "\"confidence\":0.9},\"genre\":{\"value\":\"Mystery\",\"evidence\":\"waited for the phone to ring\","
            + "\"confidence\":0.9}}";

    @TempDir
    private Path tempDir;

    private Desk desk;
    private ScriptedChatModel model;
    private SetupAssistantImpl assistant;

    @BeforeEach
    void open() {
        desk = ReviewFixtures.markdown(tempDir, BOOK);
        model = new ScriptedChatModel();
        assistant = new SetupAssistantImpl(
                desk.projects(),
                desk.segments(),
                desk.glossary(),
                desk.openProjects(),
                new PromptTemplates(),
                new ObjectMapper(),
                Clock.systemUTC());
    }

    private void synthesisSays(final String gender, final String quote, final String name) {
        model.answerTo("brief-suggestion", reply(WINDOW));
        model.answerTo(
                "brief-synthesis",
                reply("{\"narrator\":{\"gender\":\"%s\",\"quote\":\"%s\",\"name\":\"%s\"}}"
                        .formatted(gender, quote, name)));
    }

    private static Result<ChatResponse> reply(final String json) {
        return Result.ok(new ChatResponse(json, FinishReason.STOP));
    }

    private BriefSuggestion suggested() {
        return Objects.requireNonNull(
                assistant.suggestBrief(desk.projectId(), model).data());
    }

    @Test
    void likelyNarrator_keptAndMarkedLikelyWithoutQuoteWhenUnverified() {
        synthesisSays("female", "I lit a cigarette", "");

        final BriefSuggestion suggestion = suggested();

        assertThat(suggestion.narratorGender()).isEqualTo(Gender.FEMALE);
        assertThat(suggestion.evidenceOf(BriefField.NARRATOR_GENDER))
                .hasValueSatisfying(proof -> assertThat(proof)
                        .extracting(FieldEvidence::basis, FieldEvidence::quotes)
                        .containsExactly(FieldEvidence.Basis.LIKELY, List.of()));
        assertThat(suggestion.suggestedNarrator())
                .isEqualTo(
                        new Narrator(NarratorPerson.FIRST, Gender.FEMALE, Narrator.GenderOrigin.SUGGESTED, List.of()));
        assertThat(suggestion.likelyFields()).contains(ua.bookloom.api.project.BriefField.NARRATOR);
    }

    @Test
    void synthesisQuoteNamingAKnownPerson_isTheBooksWithItsQuote() {
        assertThat(desk.glossary()
                        .add(GlossaryEntry.of(
                                "ray", desk.projectId(), "Ray", null, TermType.CHARACTER, Gender.MALE, false))
                        .isOk())
                .isTrue();
        // The window gave no narrator gender, so only the synthesis's answer can decide it.
        synthesisSays("female", VOCATIVE, "Ray");

        final BriefSuggestion suggestion = suggested();

        assertThat(suggestion.suggestedNarrator())
                .isEqualTo(new Narrator(
                        NarratorPerson.FIRST, Gender.MALE, Narrator.GenderOrigin.VERIFIED, List.of(VOCATIVE)));
    }

    @Test
    void likelyGlossaryGender_provesNoNarrator() {
        assertThat(desk.glossary()
                        .add(GlossaryEntry.of(
                                        "ray", desk.projectId(), "Ray", null, TermType.CHARACTER, Gender.UNKNOWN, false)
                                .withSuggestedGender(Gender.MALE))
                        .isOk())
                .isTrue();
        synthesisSays("female", VOCATIVE, "Ray");

        final BriefSuggestion suggestion = suggested();

        assertThat(suggestion.suggestedNarrator())
                .isEqualTo(
                        new Narrator(NarratorPerson.FIRST, Gender.FEMALE, Narrator.GenderOrigin.SUGGESTED, List.of()));
    }
}

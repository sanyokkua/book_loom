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
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.BriefField;
import ua.bookloom.api.pipeline.BriefSuggestion;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.Project;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.review.ReviewFixtures;
import ua.bookloom.pipeline.review.ReviewFixtures.Desk;

/**
 * The brief suggestion gives a first-person narrator a gender only from a vocative or address the code verified, and
 * hands the brief the quote with the origin {@code VERIFIED} (15i.B4). The book has no title, so nothing is asked of
 * what the model knows; it is read in one window, then the synthesis is asked once.
 */
class SetupAssistantNarratorGenderTest {

    private static final String VOCATIVE = "Hold on tight, Ray";
    private static final String BOOK = "\"" + VOCATIVE + ",\" Danny said, and I laughed at him for a while.\n\n"
            + "The rain had not stopped for three days, and I was beginning to think the city had forgotten the sun.\n\n"
            + "I lit a cigarette and waited for the phone to ring, but it never did that night or the next one.\n";

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

    private static Result<ChatResponse> reply(final String gender, final String quote, final String name) {
        final String json = "{\"narrator\":{\"value\":\"first\",\"evidence\":\"I lit a cigarette\",\"confidence\":0.9},"
                + "\"narratorGender\":{\"value\":\"" + gender + "\",\"evidence\":\"" + quote + "\",\"name\":\"" + name
                + "\",\"confidence\":0.8}}";
        return Result.ok(new ChatResponse(json, FinishReason.STOP));
    }

    // The window's answer and an empty synthesis: no genre, audience or voice is decided from it.
    private void modelAnswers(final Result<ChatResponse> window) {
        model.answerTo("brief-suggestion", window);
        model.answerTo("brief-synthesis", Result.ok(new ChatResponse("{}", FinishReason.STOP)));
    }

    private void glossaryHoldsRay() {
        assertThat(desk.glossary()
                        .add(new GlossaryEntry(
                                "ray", desk.projectId(), "Ray", null, TermType.CHARACTER, Gender.MALE, false))
                        .isOk())
                .isTrue();
    }

    private void narratorIs(final Narrator narrator) {
        final Project stored = Objects.requireNonNull(
                        desk.projects().find(desk.projectId()).data())
                .orElseThrow();
        desk.projects().save(stored.withBrief(stored.brief().withNarrator(narrator)));
    }

    // IF the model's vocative were dropped, THEN a narrator addressed as Ray would stay of unknown gender (Oct 9 e4b
    // run). The book is shorter than one window, so it is read in one call.
    @Test
    void suggestBrief_vocativeOfAGlossaryPersonInTheWindow_suggestsThePersonsGender() {
        glossaryHoldsRay();
        modelAnswers(reply("male", VOCATIVE, "Ray"));

        final BriefSuggestion suggestion = Objects.requireNonNull(
                assistant.suggestBrief(desk.projectId(), model).data());

        assertThat(suggestion.narratorGender()).isEqualTo(Gender.MALE);
        assertThat(suggestion.evidenceOf(BriefField.NARRATOR_GENDER))
                .hasValueSatisfying(proof -> assertThat(proof.quotes()).containsExactly(VOCATIVE));
        assertThat(suggestion.suggestedNarrator())
                .isEqualTo(new Narrator(
                        NarratorPerson.FIRST, Gender.MALE, Narrator.GenderOrigin.VERIFIED, List.of(VOCATIVE)));
        // The book is unknown and the window read no genre, audience or voice, so no synthesis is asked.
        assertThat(model.requests()).extracting(ChatRequest::callKind).containsExactly(CallKind.BRIEF_SUGGESTION);
    }

    // The brief's first-person narrator with no gender is filled, not left for the Start question.
    @Test
    void suggestBrief_firstPersonNarratorWithUnknownGenderSet_isFilledFromTheVocative() {
        glossaryHoldsRay();
        narratorIs(new Narrator(NarratorPerson.FIRST, Gender.UNKNOWN));
        modelAnswers(reply("male", VOCATIVE, "Ray"));

        final BriefSuggestion suggestion = Objects.requireNonNull(
                assistant.suggestBrief(desk.projectId(), model).data());

        assertThat(suggestion.narrator()).isEqualTo(NarratorPerson.FIRST);
        assertThat(suggestion.narratorGender()).isEqualTo(Gender.MALE);
        assertThat(suggestion.evidence()).containsKey(BriefField.NARRATOR_GENDER);
    }

    // Ray is not on the first-name list on purpose, so with no glossary person the code cannot gender him.
    @Test
    void suggestBrief_vocativeOfANameNobodyKnows_leavesTheGenderUnknown() {
        modelAnswers(reply("male", VOCATIVE, "Ray"));

        final BriefSuggestion suggestion = Objects.requireNonNull(
                assistant.suggestBrief(desk.projectId(), model).data());

        assertThat(suggestion.narratorGender()).isEqualTo(Gender.UNKNOWN);
    }

    @Test
    void suggestBrief_personChoseTheGender_neverOverridesIt() {
        glossaryHoldsRay();
        narratorIs(new Narrator(NarratorPerson.FIRST, Gender.FEMALE));
        modelAnswers(reply("male", VOCATIVE, "Ray"));

        final BriefSuggestion suggestion = Objects.requireNonNull(
                assistant.suggestBrief(desk.projectId(), model).data());

        assertThat(suggestion.narratorGender()).isEqualTo(Gender.FEMALE);
        assertThat(suggestion.evidence()).doesNotContainKey(BriefField.NARRATOR_GENDER);
    }
}

package ua.bookloom.pipeline.setup;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
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
import ua.bookloom.api.project.Project;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.review.ReviewFixtures;
import ua.bookloom.pipeline.review.ReviewFixtures.Desk;

/** The brief suggestion gives a first-person narrator a gender only from a vocative or address the code verified. */
class SetupAssistantNarratorGenderTest {

    private static final String VOCATIVE = "Hang on to your ass, Jack";
    private static final String BOOK = "\"" + VOCATIVE + ",\" Bobby said, and I laughed at him for a while.\n\n"
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

    private void glossaryHoldsJack() {
        assertThat(desk.glossary()
                        .add(new GlossaryEntry(
                                "jack", desk.projectId(), "Jack", null, TermType.CHARACTER, Gender.MALE, false))
                        .isOk())
                .isTrue();
    }

    private void narratorIs(final Narrator narrator) {
        final Project stored = desk.projects().find(desk.projectId()).data().orElseThrow();
        desk.projects().save(stored.withBrief(stored.brief().withNarrator(narrator)));
    }

    // IF the model's vocative were dropped, THEN Burning Chrome's narrator would stay of unknown gender (Oct 9 e4b
    // run).
    @Test
    void suggestBrief_vocativeOfAGlossaryPersonInOneSample_suggestsThePersonsGender() {
        glossaryHoldsJack();
        model.answer(reply("male", VOCATIVE, "Jack")).answer(reply("unknown", "", ""));

        final BriefSuggestion suggestion =
                assistant.suggestBrief(desk.projectId(), model).data();

        assertThat(suggestion.narratorGender()).isEqualTo(Gender.MALE);
        assertThat(suggestion.evidenceOf(BriefField.NARRATOR_GENDER))
                .contains(new FieldEvidence(List.of(VOCATIVE), 1, 2));
    }

    // The brief's first-person narrator with no gender is filled, not left for the Start question.
    @Test
    void suggestBrief_firstPersonNarratorWithUnknownGenderSet_isFilledFromTheVocative() {
        glossaryHoldsJack();
        narratorIs(new Narrator(NarratorPerson.FIRST, Gender.UNKNOWN));
        model.answer(reply("male", VOCATIVE, "Jack")).answer(reply("unknown", "", ""));

        final BriefSuggestion suggestion =
                assistant.suggestBrief(desk.projectId(), model).data();

        assertThat(suggestion.narrator()).isEqualTo(NarratorPerson.FIRST);
        assertThat(suggestion.narratorGender()).isEqualTo(Gender.MALE);
        assertThat(suggestion.evidence()).containsKey(BriefField.NARRATOR_GENDER);
    }

    // Jack is not on the first-name list on purpose, so with no glossary person the code cannot gender him.
    @Test
    void suggestBrief_vocativeOfANameNobodyKnows_leavesTheGenderUnknown() {
        model.answer(reply("male", VOCATIVE, "Jack")).answer(reply("male", VOCATIVE, "Jack"));

        final BriefSuggestion suggestion =
                assistant.suggestBrief(desk.projectId(), model).data();

        assertThat(suggestion.narratorGender()).isEqualTo(Gender.UNKNOWN);
    }

    @Test
    void suggestBrief_personChoseTheGender_neverOverridesIt() {
        glossaryHoldsJack();
        narratorIs(new Narrator(NarratorPerson.FIRST, Gender.FEMALE));
        model.answer(reply("male", VOCATIVE, "Jack")).answer(reply("male", VOCATIVE, "Jack"));

        final BriefSuggestion suggestion =
                assistant.suggestBrief(desk.projectId(), model).data();

        assertThat(suggestion.narratorGender()).isEqualTo(Gender.FEMALE);
        assertThat(suggestion.evidence()).doesNotContainKey(BriefField.NARRATOR_GENDER);
    }
}

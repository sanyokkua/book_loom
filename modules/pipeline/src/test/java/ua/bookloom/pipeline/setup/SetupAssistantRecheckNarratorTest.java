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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.BriefSuggestion;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GenderOrigin;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.Project;
import ua.bookloom.api.project.TargetOrigin;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.review.ReviewFixtures;
import ua.bookloom.pipeline.review.ReviewFixtures.Desk;

/**
 * Start's second source of the narrator's gender (15i.T5): the quotes the brief kept are checked again against the
 * glossary as Names and style left it, with no model call and no write. Brindle is invented and on no first-name list,
 * so only the glossary can say who she is.
 */
class SetupAssistantRecheckNarratorTest {

    private static final String QUOTE = "Hold on tight, Brindle";
    private static final String ADDRESSED = "\"" + QUOTE + ",\" Danny said, and I laughed at him for a while.\n\n"
            + "The rain had not stopped for three days, and I was beginning to think the city had forgotten the sun.\n";
    private static final String SPOKEN_BY_NARRATOR = "\"Mind the step, Brindle,\" I said, and she stepped carefully"
            + " over the loose board.\n\nThe rain had not stopped for three days.\n";
    private static final Narrator PENDING =
            new Narrator(NarratorPerson.FIRST, Gender.UNKNOWN, Narrator.GenderOrigin.NONE, List.of(QUOTE));

    @TempDir
    private Path tempDir;

    private Desk desk;
    private SetupAssistantImpl assistant;

    @BeforeEach
    void openAddressedBook() {
        open(ADDRESSED);
    }

    private void open(final String book) {
        desk = ReviewFixtures.markdown(tempDir, book);
        assistant = new SetupAssistantImpl(
                desk.projects(),
                desk.segments(),
                desk.glossary(),
                desk.openProjects(),
                new PromptTemplates(),
                new ObjectMapper(),
                Clock.systemUTC());
    }

    private void narratorIs(final Narrator narrator) {
        final Project stored = Objects.requireNonNull(
                        desk.projects().find(desk.projectId()).data())
                .orElseThrow();
        desk.projects().save(stored.withBrief(stored.brief().withNarrator(narrator)));
    }

    private Narrator storedNarrator() {
        return Objects.requireNonNull(desk.projects().find(desk.projectId()).data())
                .orElseThrow()
                .brief()
                .narrator();
    }

    private void glossaryHolds(final GlossaryEntry entry) {
        assertThat(desk.glossary().add(entry).isOk()).isTrue();
    }

    private GlossaryEntry brindle(final Gender gender, final GenderOrigin origin) {
        return new GlossaryEntry(
                "brindle",
                desk.projectId(),
                "Brindle",
                null,
                TermType.CHARACTER,
                gender,
                false,
                TargetOrigin.PERSON,
                origin,
                false,
                List.of());
    }

    // IF the stored vocative were never looked at again, THEN a narrator addressed as Brindle would be asked about at
    // Start although the names pass has since confirmed Brindle is a woman.
    @ParameterizedTest
    @EnumSource(
            value = GenderOrigin.class,
            names = {"PERSON", "VERIFIED"})
    void recheckNarrator_pendingVocativeAndGlossaryPersonNowConfirmedFemale_narratorIsFemaleVerified(
            final GenderOrigin origin) {
        narratorIs(PENDING);
        glossaryHolds(brindle(Gender.FEMALE, origin));

        final Result<Narrator> checked = assistant.recheckNarrator(desk.projectId());

        assertThat(checked.data())
                .isEqualTo(new Narrator(
                        NarratorPerson.FIRST, Gender.FEMALE, Narrator.GenderOrigin.VERIFIED, List.of(QUOTE)));
    }

    // IF a suggestion counted, THEN a first-name guess nobody confirmed would be enforced on every "I" of the book.
    @Test
    void recheckNarrator_glossaryPersonOnlySuggested_leavesTheNarratorUnknown() {
        narratorIs(PENDING);
        glossaryHolds(brindle(Gender.FEMALE, GenderOrigin.SUGGESTED));

        assertThat(assistant.recheckNarrator(desk.projectId()).data()).isEqualTo(PENDING);
    }

    @Test
    void recheckNarrator_nameNotInTheGlossary_leavesTheNarratorUnchanged() {
        narratorIs(PENDING);

        assertThat(assistant.recheckNarrator(desk.projectId()).data()).isEqualTo(PENDING);
    }

    @Test
    void recheckNarrator_glossaryPersonOfUnknownGender_leavesTheNarratorUnchanged() {
        narratorIs(PENDING);
        glossaryHolds(brindle(Gender.UNKNOWN, GenderOrigin.PERSON));

        assertThat(assistant.recheckNarrator(desk.projectId()).data()).isEqualTo(PENDING);
    }

    @Test
    void recheckNarrator_noQuotesStored_leavesTheNarratorUnchanged() {
        narratorIs(new Narrator(NarratorPerson.FIRST, Gender.UNKNOWN));
        glossaryHolds(brindle(Gender.FEMALE, GenderOrigin.PERSON));

        assertThat(assistant.recheckNarrator(desk.projectId()).data())
                .isEqualTo(new Narrator(NarratorPerson.FIRST, Gender.UNKNOWN));
    }

    // IF the quote were read as the narrator's name when the narrator is the one speaking, THEN "Mind the step,
    // Brindle," I said would make the narrator a woman because the person she is warned about is one.
    @Test
    void recheckNarrator_vocativeTheNarratorSaysToSomeone_leavesTheNarratorUnknown() {
        open(SPOKEN_BY_NARRATOR);
        final Narrator pending = new Narrator(
                NarratorPerson.FIRST, Gender.UNKNOWN, Narrator.GenderOrigin.NONE, List.of("Mind the step, Brindle"));
        narratorIs(pending);
        glossaryHolds(brindle(Gender.FEMALE, GenderOrigin.PERSON));

        assertThat(assistant.recheckNarrator(desk.projectId()).data()).isEqualTo(pending);
    }

    @Test
    void recheckNarrator_personAlreadyChoseTheGender_neverChangesIt() {
        final Narrator chosen = new Narrator(NarratorPerson.FIRST, Gender.MALE);
        narratorIs(chosen);
        glossaryHolds(brindle(Gender.FEMALE, GenderOrigin.PERSON));

        assertThat(assistant.recheckNarrator(desk.projectId()).data()).isEqualTo(chosen);
    }

    @Test
    void recheckNarrator_thirdPersonNarrator_leavesItAlone() {
        final Narrator third =
                new Narrator(NarratorPerson.THIRD, Gender.UNKNOWN, Narrator.GenderOrigin.NONE, List.of(QUOTE));
        narratorIs(third);
        glossaryHolds(brindle(Gender.FEMALE, GenderOrigin.PERSON));

        assertThat(assistant.recheckNarrator(desk.projectId()).data()).isEqualTo(third);
    }

    // The port proposes and the window stores: a re-check that wrote behind the brief screen's back would be lost to
    // its next save.
    @Test
    void recheckNarrator_verified_writesNothingToTheStoredBrief() {
        narratorIs(PENDING);
        glossaryHolds(brindle(Gender.FEMALE, GenderOrigin.PERSON));

        assistant.recheckNarrator(desk.projectId());

        assertThat(storedNarrator()).isEqualTo(PENDING);
    }

    @Test
    void recheckNarrator_projectNotStored_isAValidationError() {
        final Result<Narrator> checked = assistant.recheckNarrator("no-such-project");

        assertThat(checked.isErr()).isTrue();
        assertThat(Objects.requireNonNull(checked.error()).code()).isEqualTo(ErrorCode.validation);
    }

    // The quote the model gave for a name nobody knows yet is the only thing the re-check can start from.
    @Test
    void suggestBrief_vocativeOfANameNobodyKnowsYet_keepsTheQuoteAsPendingEvidence() {
        final ScriptedChatModel model = windowAnswers(QUOTE, "Brindle");

        final BriefSuggestion suggestion = Objects.requireNonNull(
                assistant.suggestBrief(desk.projectId(), model).data());

        assertThat(suggestion.narratorGender()).isEqualTo(Gender.UNKNOWN);
        assertThat(suggestion.suggestedNarrator().evidence()).containsExactly(QUOTE);
        assertThat(suggestion.suggestedNarrator()).isEqualTo(PENDING);
    }

    // IF the narrator's own warning to someone were kept, THEN the re-check would have a quote that cannot name the
    // narrator whatever the glossary says.
    @Test
    void suggestBrief_vocativeTheNarratorSaysToSomeone_keepsNoPendingQuote() {
        open(SPOKEN_BY_NARRATOR);
        final ScriptedChatModel model = windowAnswers("Mind the step, Brindle", "Brindle");

        final BriefSuggestion suggestion = Objects.requireNonNull(
                assistant.suggestBrief(desk.projectId(), model).data());

        assertThat(suggestion.suggestedNarrator().evidence()).isEmpty();
    }

    @Test
    void suggestBrief_modelNamesAQuoteThatIsNotInTheBook_keepsNoPendingQuote() {
        final ScriptedChatModel model = windowAnswers("Well done, Brindle", "Brindle");

        final BriefSuggestion suggestion = Objects.requireNonNull(
                assistant.suggestBrief(desk.projectId(), model).data());

        assertThat(suggestion.suggestedNarrator().evidence()).isEmpty();
    }

    private static ScriptedChatModel windowAnswers(final String quote, final String name) {
        final String json = "{\"narrator\":{\"value\":\"first\",\"evidence\":\"I laughed at him\",\"confidence\":0.9},"
                + "\"narratorGender\":{\"value\":\"female\",\"evidence\":\"" + quote + "\",\"name\":\"" + name
                + "\",\"confidence\":0.8}}";
        return new ScriptedChatModel()
                .answerTo("brief-suggestion", Result.ok(new ChatResponse(json, FinishReason.STOP)))
                .answerTo("brief-synthesis", Result.ok(new ChatResponse("{}", FinishReason.STOP)));
    }
}

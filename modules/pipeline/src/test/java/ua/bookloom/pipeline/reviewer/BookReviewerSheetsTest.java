package ua.bookloom.pipeline.reviewer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.context.LexiconFilter;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;

/** {@link BookReviewerSheets}: a reviewer call is shown only what its own pairs name (15j.J14). */
class BookReviewerSheetsTest {

    private static final int ALLOWANCE = 1000;
    private static final String P = "p1";
    private static final GlossaryEntry MARROW =
            new GlossaryEntry("p1:marrow", P, "Marrow", "Мерроу", TermType.CHARACTER, Gender.FEMALE, false);
    private static final GlossaryEntry TINDLE =
            new GlossaryEntry("p1:tindle", P, "Tindle", "Тіндл", TermType.CHARACTER, Gender.MALE, false);
    private static final ReviewedPair MARROW_PAIR =
            new ReviewedPair("Book.md:0", "Marrow opened the door.", "Мерроу відчинила двері.");
    private static final ReviewedPair TINDLE_PAIR =
            new ReviewedPair("Book.md:1", "Tindle closed the lid.", "Тіндл закрив кришку.");
    private static final ReviewerCall CALL =
            new ReviewerCall(new PromptTemplates(), new ReviewReplyParser(new ObjectMapper()));
    private static final CallFrame FRAME =
            new CallFrame("en", "uk", StyleSheet.from(BookBrief.defaults("en")), ForeignPassagePolicy.KEEP);
    private static final String ALL_OK = "{\"results\":[{\"id\":\"s1\",\"status\":\"ok\"}]}";

    @Test
    void review_twoPairsNamingDifferentCharacters_eachCallIsShownOnlyItsOwn() {
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(ok(ALL_OK)).answer(ok(ALL_OK));
        final ReviewerSheets sheets = sheets(List.of(MARROW, TINDLE), List.of());

        CALL.review(List.of(MARROW_PAIR), FRAME, sheets, ReviewPass.FIRST, calls(model));
        CALL.review(List.of(TINDLE_PAIR), FRAME, sheets, ReviewPass.FIRST, calls(model));

        final String first = model.requests().get(0).messages().getLast().content();
        final String second = model.requests().get(1).messages().getLast().content();
        assertThat(first).contains("Marrow → Мерроу", "Marrow — female").doesNotContain("Tindle");
        assertThat(second).contains("Tindle → Тіндл", "Tindle — male").doesNotContain("Marrow");
    }

    @Test
    void review_replyCutAfterTheFirstPair_reAsksTheUnreadPairWithOnlyItsOwnNames() {
        final String cut = "{\"results\":[{\"id\":\"s1\",\"status\":\"ok\"},{\"id\":\"s2\",\"status\":\"edits\","
                + "\"edits\":[{\"criterion\":\"gen";
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(Result.ok(new ChatResponse(cut, FinishReason.LENGTH)))
                .answer(ok(ALL_OK));

        CALL.review(
                List.of(MARROW_PAIR, TINDLE_PAIR),
                FRAME,
                sheets(List.of(MARROW, TINDLE), List.of()),
                ReviewPass.FIRST,
                calls(model));

        assertThat(model.requests()).hasSize(2);
        assertThat(model.requests().get(0).messages().getLast().content()).contains("Marrow", "Tindle");
        assertThat(model.requests().get(1).messages().getLast().content())
                .contains("Tindle → Тіндл")
                .doesNotContain("Marrow");
    }

    @Test
    void forPairs_aliasedEntry_isListedOnceWithItsAliasesOnTheSameLine() {
        final GlossaryEntry aliased = TINDLE.withAliases(List.of("Tindles", "Tindel"));
        final ReviewedPair pair = new ReviewedPair("Book.md:2", "Tindles and Tindel waited.", "Тіндли чекали.");

        final ReviewerSheets.Sheet sheet = sheets(List.of(aliased), List.of()).forPairs(List.of(pair));

        assertThat(sheet.terms().glossaryPairs()).containsExactly("Tindle (also Tindles, Tindel) → Тіндл");
    }

    @Test
    void forPairs_suggestedTarget_isOfferedAsSuggestedAndNeverAsTheGlossary() {
        final GlossaryEntry suggested = new GlossaryEntry(
                        "p1:vale", P, "Vale", null, TermType.PLACE, Gender.UNKNOWN, false)
                .withSuggestedTarget("Вейл");
        final ReviewedPair pair = new ReviewedPair("Book.md:3", "They crossed the Vale.", "Вони перетнули Вейл.");

        final ReviewerSheets.Sheet sheet =
                sheets(List.of(suggested, TINDLE), List.of()).forPairs(List.of(pair));

        assertThat(sheet.terms().suggestedPairs()).containsExactly("Vale → Вейл");
        assertThat(sheet.terms().glossaryPairs()).isEmpty();
    }

    @Test
    void review_suggestedTarget_isLabelledNotConfirmedUnderItsOwnHeading() {
        final GlossaryEntry suggested = new GlossaryEntry(
                        "p1:vale", P, "Vale", null, TermType.PLACE, Gender.UNKNOWN, false)
                .withSuggestedTarget("Вейл");
        final ScriptedChatModel model = new ScriptedChatModel().answer(ok(ALL_OK));
        final ReviewedPair pair = new ReviewedPair("Book.md:3", "They crossed the Vale.", "Вони перетнули Вейл.");

        CALL.review(List.of(pair), FRAME, sheets(List.of(suggested), List.of()), ReviewPass.FIRST, calls(model));

        final String user = model.requests().getFirst().messages().getLast().content();
        assertThat(user).contains("[Suggested renderings — not confirmed by the person", "Vale → Вейл");
        assertThat(user).doesNotContain("[Glossary the translation had to use");
    }

    @Test
    void forPairs_lockedEntry_isLeftOutBecauseItsTokenStandsForIt() {
        final GlossaryEntry locked = TINDLE.withLocked(true);

        final ReviewerSheets.Sheet sheet = sheets(List.of(locked), List.of()).forPairs(List.of(TINDLE_PAIR));

        assertThat(sheet.terms().glossaryPairs()).isEmpty();
    }

    @Test
    void forPairs_nameOnlyInTheCandidate_isStillOffered() {
        final ReviewedPair pair = new ReviewedPair("Book.md:4", "The pilot waved.", "Pilot Marrow помахала.");

        final ReviewerSheets.Sheet sheet =
                sheets(List.of(MARROW, TINDLE), List.of()).forPairs(List.of(pair));

        assertThat(sheet.terms().glossaryPairs()).containsExactly("Marrow → Мерроу");
        assertThat(sheet.characters()).containsExactly("Marrow — female");
    }

    @Test
    void forPairs_nameOnlyDeclinedInTheCandidate_isStillOffered() {
        final ReviewedPair pair = new ReviewedPair("Book.md:6", "The pilot waved.", "Мерроу помахала.");

        final ReviewerSheets.Sheet sheet =
                sheets(List.of(MARROW, TINDLE), List.of()).forPairs(List.of(pair));

        assertThat(sheet.terms().glossaryPairs()).containsExactly("Marrow → Мерроу");
        assertThat(sheet.characters()).containsExactly("Marrow — female");
    }

    @Test
    void forPairs_usualRendering_isOfferedOnlyToTheCallWhosePairsNameTheTerm() {
        final LexiconEntry lamp = new LexiconEntry(P, "lantern", List.of(), "ліхтар", null, null);
        final ReviewedPair named = new ReviewedPair("Book.md:5", "She lit the lantern.", "Вона запалила ліхтар.");
        final ReviewerSheets sheets = sheets(List.of(), List.of(lamp));

        assertThat(sheets.forPairs(List.of(named)).terms().usualRenderings()).containsExactly("lantern → ліхтар");
        assertThat(sheets.forPairs(List.of(TINDLE_PAIR)).terms().usualRenderings())
                .isEmpty();
    }

    @Test
    void forPairs_recurringTermTheGlossaryHolds_isNotRepeatedAsUsual() {
        final GlossaryEntry held =
                new GlossaryEntry("p1:lantern", P, "lantern", "ліхтар", TermType.TERM, Gender.UNKNOWN, false);
        final LexiconEntry lamp = new LexiconEntry(P, "lantern", List.of(), "ліхтар", null, null);
        final ReviewedPair named = new ReviewedPair("Book.md:5", "She lit the lantern.", "Вона запалила ліхтар.");

        final ReviewerSheets.Sheet sheet = sheets(List.of(held), List.of(lamp)).forPairs(List.of(named));

        assertThat(sheet.terms().usualRenderings()).isEmpty();
        assertThat(sheet.terms().glossaryPairs()).containsExactly("lantern → ліхтар");
    }

    private static BookReviewerSheets sheets(final List<GlossaryEntry> glossary, final List<LexiconEntry> lexicon) {
        return new BookReviewerSheets(glossary, lexicon, LexiconFilter.NONE, "en", ALLOWANCE);
    }

    private static ModelCalls calls(final ScriptedChatModel model) {
        return (kind, segmentId, request) -> model.chat(request);
    }

    private static Result<ChatResponse> ok(final String content) {
        return Result.ok(new ChatResponse(content, FinishReason.STOP));
    }
}

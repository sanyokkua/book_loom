package ua.bookloom.pipeline.index;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.document.UnitRole;

/**
 * Windows around the mentions of one name: which mentions are shown (the first, then one per equal stretch of the
 * rest, narration preferred), how wide each window is (neighbouring segments inside the mention's unit) and how it is
 * trimmed to the token budget. The budget is estimated for the book's declared language (English: 4 characters per
 * token, times 1.15, rounded up).
 */
class BookIndexWindowsTest {

    private static final int ROOMY = 1000;
    private static final String RAIN = "Rain fell on the quiet road all night.";
    private static final String IVO_SENTENCE = "Then Ivo came down to the water.";
    private static final String BOAT = "The boat was gone by the morning.";
    private static final String NARRATION = "Ivo waited by the door.";
    private static final String SPEECH = "“Where is Ivo?” she asked.";

    private static BookIndex trimBook() {
        return BookIndex.of(IndexTestBooks.oneUnit(
                "The first line is quiet and calm. It ends here.",
                RAIN + " " + IVO_SENTENCE + " " + BOAT,
                "Nobody spoke of it again. The end came soon."));
    }

    private static List<Window> windowsOf(final BookIndex index, final WindowPlan plan) {
        return index.windows(index.mentions(List.of("Ivo"), Case.SENSITIVE), plan);
    }

    // A plan that grows forward first takes the sentence after the mention before the one before it.
    @Test
    void windows_budgetForOneNeighbourSentenceGrowingForward_keepsTheOneAfter() {
        final Window window = windowsOf(trimBook(), new WindowPlan(1, 1, 25, WindowPlan.Growth.FORWARD_FIRST))
                .getFirst();

        assertThat(window.text()).isEqualTo(IVO_SENTENCE + " " + BOAT);
        assertThat(window.text().substring(window.markStart(), window.markEnd()))
                .isEqualTo("Ivo");
    }

    // IF the budget is roomy, THEN the window is the mention's segment and one neighbour each side, whole.
    @Test
    void windows_roomyBudget_holdsTheSegmentAndItsNeighboursWhole() {
        final Window window = windowsOf(trimBook(), new WindowPlan(1, 1, ROOMY)).getFirst();

        assertThat(window.firstOrdinal()).isZero();
        assertThat(window.lastOrdinal()).isEqualTo(2);
        assertThat(window.text())
                .contains("It ends here.", RAIN, IVO_SENTENCE, BOAT, "Nobody spoke of it again.")
                .containsSubsequence(RAIN, IVO_SENTENCE, BOAT);
    }

    // IF the budget is smaller than the mention's own sentence, THEN the sentence is still kept, alone.
    @Test
    void windows_tinyBudget_keepsOnlyTheMentionsSentence() {
        final Window window = windowsOf(trimBook(), new WindowPlan(1, 1, 1)).getFirst();

        assertThat(window.text()).isEqualTo(IVO_SENTENCE);
        assertThat(window.markStart()).isEqualTo(5);
        assertThat(window.markEnd()).isEqualTo(8);
        assertThat(window.firstOrdinal()).isEqualTo(1);
        assertThat(window.lastOrdinal()).isEqualTo(1);
    }

    // IF the sentence before and the sentence after each fit alone but not together, THEN the one before is kept
    // (ties go before).
    @Test
    void windows_budgetForOneNeighbourSentence_keepsTheOneBefore() {
        final Window window = windowsOf(trimBook(), new WindowPlan(1, 1, 25)).getFirst();

        assertThat(window.text()).isEqualTo(RAIN + " " + IVO_SENTENCE);
        assertThat(window.markStart()).isEqualTo(44);
        assertThat(window.markEnd()).isEqualTo(47);
    }

    // IF the budget holds the mention's whole segment but not the next sentence out, THEN the window stops there.
    @Test
    void windows_budgetForTheWholeSegment_stopsAtTheSegmentsEdge() {
        final Window window = windowsOf(trimBook(), new WindowPlan(1, 1, 33)).getFirst();

        assertThat(window.text()).isEqualTo(RAIN + " " + IVO_SENTENCE + " " + BOAT);
        assertThat(window.firstOrdinal()).isEqualTo(1);
        assertThat(window.lastOrdinal()).isEqualTo(1);
    }

    // IF the budget reaches into the previous segment by one sentence, THEN only its last sentence comes in.
    @Test
    void windows_budgetReachingIntoAnotherSegment_takesWholeSentencesOnly() {
        final Window window = windowsOf(trimBook(), new WindowPlan(1, 1, 40)).getFirst();

        assertThat(window.text()).startsWith("It ends here.").endsWith(BOAT);
        assertThat(window.text()).doesNotContain("The first line", "Nobody");
        assertThat(window.firstOrdinal()).isZero();
        assertThat(window.lastOrdinal()).isEqualTo(1);
    }

    // IF the window is trimmed to any budget, THEN it still holds its mention, marked by its range in the text.
    @ParameterizedTest
    @ValueSource(ints = {1, 25, 33, 40, ROOMY})
    void windows_anyBudget_holdsTheMentionAtItsMarkedRange(final int budget) {
        final Window window =
                windowsOf(trimBook(), new WindowPlan(1, 1, budget)).getFirst();

        assertThat(window.text().substring(window.markStart(), window.markEnd()))
                .isEqualTo("Ivo");
        assertThat(window.mention().ordinal()).isBetween(window.firstOrdinal(), window.lastOrdinal());
    }

    // IF a window joined its paragraphs with a space, THEN a speaker's dash-led line in the next paragraph would read
    // as
    // narration: segments are joined by a line break, the sentences of one segment by a space.
    @Test
    void windows_neighbouringSegments_areJoinedByALineBreak() {
        final Window window = windowsOf(trimBook(), new WindowPlan(1, 1, ROOMY)).getFirst();

        assertThat(window.text())
                .isEqualTo("The first line is quiet and calm. It ends here.\n" + RAIN + " " + IVO_SENTENCE + " " + BOAT
                        + "\nNobody spoke of it again. The end came soon.");
    }

    // IF a sentence lost its opening quotation mark, THEN speech after the name would read as narration.
    @Test
    void windows_sentenceOpeningWithAQuote_keepsItsQuotationMarks() {
        final String paragraph = "Ivo stood by the window. “She will come,” said Tovrin. — Not today.";
        final BookIndex index = narrationBook(paragraph, "— She is late, — Tovrin said.");

        final Window window = windowsOf(index, new WindowPlan(1, 1, ROOMY)).getFirst();

        assertThat(window.text()).isEqualTo(paragraph + "\n— She is late, — Tovrin said.");
    }

    // IF one sentence ran past the budget, THEN it would fill the window alone, however long: a sentence over the
    // budget (and over 50 tokens) is cut at word boundaries around the mention, the word before first. 60 tokens are
    // 208 English characters: 34 words before, 33 after.
    @Test
    void windows_sentenceOverTheBudget_isCutAtWordsAroundTheMention() {
        final BookIndex index = narrationBook("aa ".repeat(200) + "Ivo" + " aa".repeat(200) + ".");

        final Window window = windowsOf(index, new WindowPlan(1, 1, 60)).getFirst();

        assertThat(window.text()).isEqualTo("…" + "aa ".repeat(34) + "Ivo" + " aa".repeat(33) + "…");
        assertThat(window.text().substring(window.markStart(), window.markEnd()))
                .isEqualTo("Ivo");
    }

    // A caller may prefer some mentions (the name review prefers one the code can give a pronoun): the first preferred
    // mention of a stretch is shown instead of its middle one.
    @Test
    void windows_preferredMention_isShownInsteadOfItsStretchsMiddle() {
        final BookIndex index = tenMentions("NNNNNNNNNN");

        final List<Integer> shown = index
                .windows(
                        index.mentions(List.of("Ivo"), Case.SENSITIVE),
                        new WindowPlan(4, 0, ROOMY),
                        mention -> mention.ordinal() == 1)
                .stream()
                .map(window -> window.mention().ordinal())
                .toList();

        assertThat(shown).containsExactly(0, 1, 5, 8);
    }

    private static BookIndex twoUnits() {
        return BookIndex.of(IndexTestBooks.document(
                IndexTestBooks.unit(
                        "u1",
                        UnitRole.BODY,
                        "Line 0 holds nothing.",
                        "Line 1 holds nothing.",
                        "Line 2 brings Ivo.",
                        "Line 3 holds nothing.",
                        "Line 4 brings Ivo."),
                IndexTestBooks.unit(
                        "u2", UnitRole.BODY, "Line 5 brings Ivo.", "Line 6 holds nothing.", "Line 7 holds nothing.")));
    }

    // IF a mention is at the start or end of its unit, THEN the window stops at the unit's edge.
    @Test
    void windows_mentionsAtAUnitsEdges_neverCrossIntoTheNextOrPreviousUnit() {
        final List<Window> windows = windowsOf(twoUnits(), new WindowPlan(3, 1, ROOMY));

        assertThat(windows)
                .extracting(window -> window.mention().ordinal(), Window::firstOrdinal, Window::lastOrdinal)
                .containsExactly(tuple(2, 1, 3), tuple(4, 3, 4), tuple(5, 5, 6));
        assertThat(windows.get(1).text()).contains("Line 3 holds nothing.").doesNotContain("Line 5");
        assertThat(windows.get(2).text()).contains("Line 6 holds nothing.").doesNotContain("Line 4");
    }

    @Test
    void windows_moreNeighboursThanTheUnitHolds_stopAtTheUnitsEdges() {
        final List<Window> windows = windowsOf(twoUnits(), new WindowPlan(3, 10, ROOMY));

        assertThat(windows)
                .extracting(window -> window.mention().ordinal(), Window::firstOrdinal, Window::lastOrdinal)
                .containsExactly(tuple(2, 0, 4), tuple(4, 0, 4), tuple(5, 5, 7));
    }

    @Test
    void windows_noNeighbours_isTheMentionsSegmentAlone() {
        assertThat(windowsOf(twoUnits(), new WindowPlan(3, 0, ROOMY)))
                .extracting(Window::firstOrdinal, Window::lastOrdinal)
                .containsExactly(tuple(2, 2), tuple(4, 4), tuple(5, 5));
    }

    private static BookIndex narrationBook(final String... lines) {
        return BookIndex.of(IndexTestBooks.oneUnit(lines));
    }

    private static List<Integer> shownOrdinals(final BookIndex index, final int count) {
        return windowsOf(index, new WindowPlan(count, 0, ROOMY)).stream()
                .map(window -> window.mention().ordinal())
                .toList();
    }

    private static BookIndex tenMentions(final String pattern) {
        return narrationBook(pattern.chars()
                .mapToObj(letter -> letter == 'N' ? NARRATION : SPEECH)
                .toArray(String[]::new));
    }

    // IF a name is mentioned ten times in narration, THEN four windows show the first mention and the middle mention
    // of each of the three equal stretches the other nine are cut into.
    @Test
    void windows_tenMentionsAllNarration_showsTheFirstAndTheMiddleOfEachStretch() {
        assertThat(shownOrdinals(tenMentions("NNNNNNNNNN"), 4)).containsExactly(0, 2, 5, 8);
    }

    // IF a stretch holds narration, THEN the window is taken from its narration (middle of those); a stretch that is
    // all speech falls back to its middle mention; the first mention is shown whatever it is.
    @Test
    void windows_stretchesMixingSpeechAndNarration_preferNarration() {
        // mentions: 0 N | 1 S 2 S 3 N | 4 S 5 S 6 S | 7 N 8 N 9 N
        assertThat(shownOrdinals(tenMentions("NSSNSSSNNN"), 4)).containsExactly(0, 3, 5, 8);
    }

    @Test
    void windows_firstMentionInSpeech_isStillShown() {
        assertThat(shownOrdinals(tenMentions("SNNNNNNNNN"), 2)).startsWith(0);
    }

    // IF fewer windows are asked for than there are mentions, THEN there are never more windows than asked.
    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4})
    void windows_countBelowTheMentions_neverReturnsMoreThanCount(final int count) {
        assertThat(shownOrdinals(tenMentions("NNNNNNNNNN"), count)).hasSize(count);
    }

    @Test
    void windows_countOneAndTwo_showTheFirstThenTheMiddleOfTheRest() {
        assertThat(shownOrdinals(tenMentions("NNNNNNNNNN"), 1)).containsExactly(0);
        assertThat(shownOrdinals(tenMentions("NNNNNNNNNN"), 2)).containsExactly(0, 5);
    }

    // IF there are fewer mentions than windows asked for, THEN every mention is shown once.
    @Test
    void windows_countAboveTheMentions_showsEachMentionOnce() {
        final BookIndex index = narrationBook(NARRATION, NARRATION, NARRATION);

        assertThat(shownOrdinals(index, 6)).containsExactly(0, 1, 2);
    }

    // IF a later mention shares its sentence with a window already chosen, THEN that sentence is not shown twice.
    @Test
    void windows_aMentionInAnAlreadyShownSentence_isSkipped() {
        final BookIndex index = narrationBook("Ivo saw Ivo again.", "Ivo left.", "Ivo came back.");

        assertThat(windowsOf(index, new WindowPlan(3, 0, ROOMY)))
                .extracting(
                        window -> window.mention().ordinal(),
                        window -> window.mention().start())
                .containsExactly(tuple(0, 0), tuple(2, 0));
    }

    @Test
    void windows_noMentionsOrNoCount_isEmpty() {
        final BookIndex index = trimBook();

        assertThat(index.windows(List.of(), new WindowPlan(4, 1, ROOMY))).isEmpty();
        assertThat(windowsOf(index, new WindowPlan(0, 1, ROOMY))).isEmpty();
    }

    // IF the mentions alternate between narration and speech, THEN the windows come back in reading order, each
    // stretch giving its middle narration mention.
    @Test
    void windows_alternatingMentions_areReturnedInReadingOrder() {
        assertThat(shownOrdinals(tenMentions("NSNSNSNSNS"), 5)).containsExactly(0, 2, 4, 6, 8);
    }
}

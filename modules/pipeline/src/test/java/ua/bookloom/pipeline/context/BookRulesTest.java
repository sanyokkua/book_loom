package ua.bookloom.pipeline.context;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.context.FrameFixtures.BOOK_RULES;
import static ua.bookloom.pipeline.context.FrameFixtures.DIALOGUE;
import static ua.bookloom.pipeline.context.FrameFixtures.NARRATION;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.pipeline.index.BookIndex;
import ua.bookloom.pipeline.prompt.LanguageRules;

class BookRulesTest {

    @ParameterizedTest
    @CsvSource({"0.0,0", "0.049,0", "0.05,1", "0.4,1"})
    void choose_noDialogue_noDialogueRule(final double share, final int expected) {
        final RuleFacts facts = new RuleFacts(false, share, false, false, false);

        assertThat(BOOK_RULES.choose(facts, "uk"))
                .filteredOn(line -> line.rule() == BookRule.DIALOGUE)
                .hasSize(expected);
        assertThat(BOOK_RULES.choose(facts, "uk"))
                .filteredOn(line -> line.rule() == BookRule.ADDRESS)
                .hasSize(expected);
    }

    @Test
    void choose_englishTarget_noAgreementLine() {
        final Document book = FrameFixtures.book(NARRATION, DIALOGUE);
        final BookBrief toEnglish =
                FrameFixtures.brief("ru", "en").withNarrator(new Narrator(NarratorPerson.FIRST, Gender.FEMALE));
        final BookBrief toUkrainian = toEnglish.withLanguages("ru", "uk");

        final RuleFacts english = RuleFacts.of(
                BookIndex.of(book), book, toEnglish, FrameFixtures.emptyMap(toEnglish), LanguageRules.bundled());
        final RuleFacts ukrainian = RuleFacts.of(
                BookIndex.of(book), book, toUkrainian, FrameFixtures.emptyMap(toUkrainian), LanguageRules.bundled());

        assertThat(BOOK_RULES.choose(english, "en")).extracting(RuleLine::rule).doesNotContain(BookRule.AGREEMENT);
        assertThat(BOOK_RULES.choose(ukrainian, "uk"))
                .extracting(RuleLine::rule)
                .contains(BookRule.AGREEMENT);
    }

    @Test
    void choose_everyFact_rulesInFixedOrderWithTargetWording() {
        final RuleFacts facts = new RuleFacts(true, 0.3, true, true, true);

        assertThat(BOOK_RULES.choose(facts, "uk"))
                .extracting(RuleLine::rule)
                .containsExactly(
                        BookRule.MARKS,
                        BookRule.DIALOGUE,
                        BookRule.ADDRESS,
                        BookRule.NUMBERS,
                        BookRule.AGREEMENT,
                        BookRule.INFLECT);
        assertThat(BOOK_RULES.choose(facts, "uk"))
                .filteredOn(line -> line.rule() == BookRule.AGREEMENT)
                .extracting(RuleLine::text)
                .containsExactly(
                        "Past-tense verbs and adjectives agree with the subject's gender and number (він пішов, вона пішла).");
    }

    @Test
    void ruleFacts_bookWithMarksNumbersAndDialogue_statesThem() {
        final Document book = FrameFixtures.book(NARRATION, DIALOGUE, FrameFixtures.MARKED);
        final BookBrief brief = FrameFixtures.brief("ru", "uk");

        final RuleFacts facts =
                RuleFacts.of(BookIndex.of(book), book, brief, FrameFixtures.emptyMap(brief), LanguageRules.bundled());

        assertThat(facts.hasMarks()).isTrue();
        assertThat(facts.hasNumbers()).isTrue();
        assertThat(facts.dialogueShare()).isGreaterThan(BookRules.DIALOGUE_SHARE_MIN);
        assertThat(facts.genderedCast()).isFalse();
        assertThat(facts.targetInflects()).isTrue();
    }
}

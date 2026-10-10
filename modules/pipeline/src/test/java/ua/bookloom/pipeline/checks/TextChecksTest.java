package ua.bookloom.pipeline.checks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** The deterministic text checks, in Ukrainian (Cyrillic) and Polish (Latin) to show nothing is script-specific. */
class TextChecksTest {

    private static final String ENGLISH_PARAGRAPH =
            "The night was cold and the streets were empty, and nobody had seen the old man since the evening.";
    private static final String UKRAINIAN_PARAGRAPH =
            "Ніч була холодна, а вулиці порожні, і ніхто не бачив старого відтоді, як настав вечір.";
    private static final String POLISH_PARAGRAPH =
            "Noc była zimna, a ulice puste, i nikt nie widział starego człowieka od wieczora.";

    private static List<CheckFinding> uk(final String source, final String target) {
        return TextChecks.run(source, target, "en", "uk");
    }

    private static List<CheckFinding> pl(final String source, final String target) {
        return TextChecks.run(source, target, "en", "pl");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "Latin letter and digit in a Cyrillic word|He studied hard all year.|Він наполегливо навчg3вся цілий рік.|навчg3вся",
                "Latin o among Cyrillic letters|They explained the import.|Вони пояснили імпoву причину.|імпoву",
                "Greek letter among Cyrillic letters|He was a very kind man.|Він був дуже добрим чоловіκом.|чоловіκом",
            })
    void run_mixedScriptWordInUkrainian_isBlockingWithTheWordAsSpan(
            final String name, final String source, final String target, final String word) {
        assertThat(uk(source, target))
                .extracting(CheckFinding::kind, finding -> finding.span().text(), CheckFinding::blocking)
                .containsExactly(tuple(FindingKind.MIXED_SCRIPT, word, true));
    }

    @Test
    void run_mixedScriptWordInPolish_isBlockingWithTheWordAsSpan() {
        // the Cyrillic "о" (U+043E) among Latin letters: the same defect in a Latin-script target
        final String target = "Uczył się pilnie cały rоk.";

        assertThat(pl("He studied hard all year.", target))
                .extracting(CheckFinding::kind, finding -> finding.span().text(), CheckFinding::blocking)
                .containsExactly(tuple(FindingKind.MIXED_SCRIPT, "rоk", true));
    }

    @Test
    void run_garbledWordInTheTargetScript_isNotDetectable() {
        // "розлізяв" is a coined word in the right alphabet: only a model or a dictionary could tell, so the
        // deterministic checks must stay silent rather than guess (the judge owns it)
        assertThat(uk("He bent over the map.", "Він розлізяв над картою.")).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "a name left in Latin|Harry met Hermione.|Гаррі зустрів Hermione.",
                "an ordinal suffix after digits|The 90s were calm.|90х були спокійними.",
                "a unit after a number|It weighed 5 kg.|Це важило 5 кг.",
                "a chemical formula|It smelled of H2O.|Пахло H2O.",
            })
    void run_legitimateLatinOrDigitsBesideCyrillic_raisesNothing(
            final String name, final String source, final String target) {
        assertThat(uk(source, target)).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "missing close|“Yes, sir,” said the boy.|«Ні, сер, — відповів хлопчик.",
                "stray close|“Stop!” he shouted. “Go away!”|«Зупинись!» — крикнув він. — Геть!»",
            })
    void run_unbalancedQuotesOverABalancedSource_isBlocking(
            final String name, final String source, final String target) {
        final List<CheckFinding> findings = uk(source, target);

        assertThat(findings)
                .extracting(CheckFinding::kind, CheckFinding::blocking)
                .containsExactly(tuple(FindingKind.UNBALANCED_QUOTES, true));
        assertThat(findings.getFirst().explanation()).contains("source's quotes are balanced");
    }

    @Test
    void run_missingCloseSpan_startsAtTheOpeningMark() {
        final CheckFinding finding =
                uk("“Yes, sir,” said the boy.", "«Ні, сер, — відповів хлопчик.").getFirst();

        assertThat(finding.span().start()).isZero();
        assertThat(finding.span().text()).startsWith("«Ні, сер");
    }

    @Test
    void run_quotationThatRunsOnInTheSource_isNotBlamedOnTheTarget() {
        assertThat(uk("“I was there when it began,", "«Я був там, коли все почалося,"))
                .isEmpty();
    }

    @Test
    void run_dashDialogueAndOtherPunctuation_isNeverReadAsAQuote() {
        assertThat(uk("“Come here,” she said.", "— Іди сюди, — сказала вона. (Тихо!)"))
                .isEmpty();
    }

    @Test
    void run_balancedAndNestedQuotes_raisesNothing() {
        assertThat(uk("“He said ‘no’,” she said.", "«Він сказав „ні“», — сказала вона."))
                .isEmpty();
    }

    @Test
    void run_turkishApostropheAfterAName_isNotAStrayQuote() {
        assertThat(TextChecks.run("He went to Ankara.", "Ankara’ya gitti.", "en", "tr"))
                .isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"uk", "ru", "be", "de", "cs", "sk", "sl", "hr", "bg"})
    void run_balancedEnglishCurlyQuotes_isOnlyASoftNote(final String language) {
        final List<CheckFinding> findings =
                TextChecks.run("“No, sir,” said the boy.", "“Ні, сер,” — відповів хлопчик.", "en", language);

        assertThat(findings)
                .extracting(CheckFinding::kind, CheckFinding::blocking)
                .containsExactly(tuple(FindingKind.UNBALANCED_QUOTES, false));
        assertThat(findings.getFirst().explanation()).contains("English quote marks");
    }

    @Test
    void run_englishCurlyQuoteLeftOpenInUkrainian_isStillBlocking() {
        assertThat(uk("“No, sir,” said the boy.", "“Ні, сер, — відповів хлопчик."))
                .extracting(CheckFinding::kind, CheckFinding::blocking)
                .containsExactly(tuple(FindingKind.UNBALANCED_QUOTES, true));
    }

    @Test
    void run_polishQuotesLeftOpen_isBlocking() {
        assertThat(pl("“Come here,” she said.", "„Chodź tu, powiedziała."))
                .extracting(CheckFinding::kind, CheckFinding::blocking)
                .containsExactly(tuple(FindingKind.UNBALANCED_QUOTES, true));
    }

    @Test
    void run_polishQuotesBalanced_raisesNothing() {
        assertThat(pl("“Come here,” she said.", "„Chodź tu” — powiedziała.")).isEmpty();
    }

    @Test
    void run_englishParagraphInUkrainianTarget_isBlockingLeftover() {
        assertThat(uk(ENGLISH_PARAGRAPH, ENGLISH_PARAGRAPH))
                .extracting(CheckFinding::kind, CheckFinding::blocking)
                .contains(tuple(FindingKind.LEFTOVER_LANGUAGE, true));
    }

    @Test
    void run_englishParagraphInPolishTarget_isBlockingLeftover() {
        // the same script on both sides: only the source language's function words can tell it is not Polish
        assertThat(pl(ENGLISH_PARAGRAPH, ENGLISH_PARAGRAPH))
                .extracting(CheckFinding::kind, CheckFinding::blocking)
                .containsExactly(tuple(FindingKind.LEFTOVER_LANGUAGE, true));
    }

    @Test
    void run_shortEnglishLine_isLeftToTheEchoCheck() {
        assertThat(uk("The night was cold.", "The night was cold.")).isEmpty();
    }

    @Test
    void run_englishParagraphAmongTranslatedOnes_flagsOnlyThatParagraph() {
        final String target = UKRAINIAN_PARAGRAPH + "\n" + ENGLISH_PARAGRAPH;

        assertThat(uk(ENGLISH_PARAGRAPH + "\n" + ENGLISH_PARAGRAPH, target))
                .extracting(CheckFinding::kind, finding -> finding.span().text())
                .containsExactly(tuple(FindingKind.LEFTOVER_LANGUAGE, ENGLISH_PARAGRAPH));
    }

    @Test
    void run_longTranslatedParagraphs_raiseNothing() {
        assertThat(uk(ENGLISH_PARAGRAPH, UKRAINIAN_PARAGRAPH)).isEmpty();
        assertThat(pl(ENGLISH_PARAGRAPH, POLISH_PARAGRAPH)).isEmpty();
    }

    @Test
    void run_sourceLanguageUnknown_skipsTheLanguageIdentityCheck() {
        assertThat(TextChecks.run(ENGLISH_PARAGRAPH, ENGLISH_PARAGRAPH, null, "uk"))
                .isEmpty();
    }

    @Test
    void run_repeatedWord_isASoftFindingWithTheRepeatAsSpan() {
        assertThat(uk("It is all the same to me, still.", "Мені одно одно таки."))
                .extracting(CheckFinding::kind, finding -> finding.span().text(), CheckFinding::blocking)
                .containsExactly(tuple(FindingKind.DUPLICATE_WORD, "одно одно", false));
    }

    @Test
    void run_repeatThatTheSourceMakesToo_isLeftAlone() {
        assertThat(uk("It was very very cold.", "Було дуже дуже холодно.")).isEmpty();
    }

    @Test
    void run_repeatedWordInPolish_isASoftFinding() {
        assertThat(pl("It is cold, so very cold.", "Jest zimno, tak tak zimno."))
                .extracting(CheckFinding::kind, CheckFinding::blocking)
                .containsExactly(tuple(FindingKind.DUPLICATE_WORD, false));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "a doubled space|He left, and she stayed.|Він пішов,  а вона лишилась.",
                "a space before a full stop after a bracket|He left (quietly).|Він пішов (тихо) .",
                "a space inside a bracket|He left (quietly).|Він пішов ( тихо).",
            })
    void run_spacingArtefactTheSourceLacks_isASoftSpacingFinding(
            final String name, final String source, final String target) {
        assertThat(uk(source, target))
                .extracting(CheckFinding::kind, CheckFinding::blocking)
                .containsExactly(tuple(FindingKind.SPACING, false));
    }

    @Test
    void run_spacingArtefactTheSourceHasToo_isLeftAlone() {
        assertThat(uk("He left,  and she stayed.", "Він пішов,  а вона лишилась."))
                .isEmpty();
    }

    @Test
    void run_frenchStylePunctuationSpacing_isNeverAnError() {
        assertThat(TextChecks.run("Come here! Why? Now: go.", "Viens ici ! Pourquoi ? Maintenant : va.", "en", "fr"))
                .isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "an ISBN|ISBN 978-3-16-148410-0|ISBN 978-3-16-148410-0",
                "a web address|www.example.com/books/1|www.example.com/books/1",
                "an e-mail address|write to editor@example.com|write to editor@example.com",
                "bare numbers|1984 2003 42|1984 2003 42",
            })
    void run_digitsAndLocatorsOnly_raiseNothing(final String name, final String source, final String target) {
        assertThat(uk(source, target)).isEmpty();
    }

    @Test
    void run_findingNote_quotesTheSpanThenTheExplanation() {
        final CheckFinding finding =
                uk("He studied hard all year.", "Він навчg3вся цілий рік.").getFirst();

        assertThat(finding.note()).startsWith("\"навчg3вся\" — The word mixes alphabets");
    }

    // IF a run of source Latin words left in a Cyrillic target were let through, THEN English reaches the book.
    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "a two-word run|He read the Daily Courier on the train.|Він читав Daily Courier у поїзді.|Daily Courier",
                "a three-word run in other case|They sold Old Harbour Press books.|Вони продавали книжки old harbour press.|old harbour press",
            })
    void run_sourceLatinRunLeftInCyrillicTarget_isBlockingWithTheRunAsSpan(
            final String name, final String source, final String target, final String run) {
        assertThat(uk(source, target))
                .filteredOn(CheckFinding::blocking)
                .extracting(finding -> finding.kind(), finding -> finding.span().text())
                .containsExactly(tuple(FindingKind.LATIN_RUN, run));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "a single kept word|He read Courier on the train.|Він читав Courier у поїзді.",
                "a run the source does not have|He read the paper on the train.|Він читав Daily Courier у поїзді.",
            })
    void run_latinWordsThatAreNotASourceRun_areNotBlocked(final String name, final String source, final String target) {
        assertThat(uk(source, target)).noneMatch(CheckFinding::blocking);
    }

    // IF a name the glossary keeps in Latin counted as a left-over run, THEN Keep original could never pass.
    @Test
    void run_latinRunThatIsAGlossaryRendering_isNotALatinRun() {
        final List<CheckFinding> found = TextChecks.run(
                "Bobby Quine sat at the bar.",
                "Bobby Quine сидів біля стійки.",
                "en",
                "uk",
                List.of("Bobby Quine → Bobby Quine"));

        assertThat(found).extracting(CheckFinding::kind).doesNotContain(FindingKind.LATIN_RUN);
    }

    @Test
    void run_latinRunBesideAGlossaryRendering_isStillALatinRun() {
        final List<CheckFinding> found = TextChecks.run(
                "Bobby Quine read the Daily Courier on the train.",
                "Bobby Quine читав Daily Courier у нічному поїзді.",
                "en",
                "uk",
                List.of("Bobby Quine → Bobby Quine"));

        assertThat(found)
                .filteredOn(finding -> finding.kind() == FindingKind.LATIN_RUN)
                .extracting(finding -> finding.span().text())
                .containsExactly("Daily Courier");
    }

    @Test
    void run_latinRunInALatinTarget_isNotAFinding() {
        assertThat(pl("He read the Daily Courier on the train.", "Czytał Daily Courier w pociągu."))
                .isEmpty();
    }

    // IF a listed Russian word got no exact replacement, THEN the fix call has to guess the Ukrainian word.
    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "lower case|He came too.|Він тоже прийшов.|тоже|теж",
                "capitalised|Too late.|Тоже пізно.|Тоже|Теж",
            })
    void run_listedRussianWord_isSoftWithTheExactReplacement(
            final String name, final String source, final String target, final String word, final String replacement) {
        final List<CheckFinding> found = uk(source, target).stream()
                .filter(finding -> finding.kind() == FindingKind.FOREIGN_WORD)
                .toList();

        assertThat(found).singleElement().satisfies(finding -> {
            assertThat(finding.blocking()).isFalse();
            assertThat(finding.span().text()).isEqualTo(word);
            assertThat(finding.explanation()).contains("\"" + replacement + "\"");
        });
    }

    @Test
    void run_listedWordThatTheSourceAlsoHas_isLeftAlone() {
        assertThat(uk("Он сказал тоже.", "Він сказав «тоже».")).noneMatch(f -> f.kind() == FindingKind.FOREIGN_WORD);
    }

    // IF the lost name were not reported at run time, THEN the draft keeps the sentence without the place.
    @Test
    void run_glossaryNameLostFromTheTarget_isSoftWithTheRenderingToPutBack() {
        final List<CheckFinding> found = TextChecks.run(
                "They reached Zurich at dawn.",
                "Вони дісталися до міста на світанку.",
                "en",
                "uk",
                List.of("Zurich → Цюріх"));

        assertThat(found).singleElement().satisfies(finding -> {
            assertThat(finding.kind()).isEqualTo(FindingKind.NAME_MISSING);
            assertThat(finding.blocking()).isFalse();
            assertThat(finding.explanation()).contains("\"Цюріх\"");
        });
    }

    // IF the model wrote the name another way, THEN the finding must name that word and the exact replacement.
    @Test
    void run_glossaryNameSpelledAnotherWay_isSoftWithTheWordAndTheReplacement() {
        final List<CheckFinding> found =
                TextChecks.run("David stayed home.", "Тавид залишився вдома.", "en", "uk", List.of("David → Давид"));

        assertThat(found).singleElement().satisfies(finding -> {
            assertThat(finding.kind()).isEqualTo(FindingKind.NAME_SPELLING);
            assertThat(finding.blocking()).isFalse();
            assertThat(finding.span().text()).isEqualTo("Тавид");
            assertThat(finding.explanation()).contains("replace \"Тавид\" with \"Давид\"");
        });
    }

    // IF a pronoun that folds into the name were its spelling, THEN the fix would put the name over every "Він".
    @Test
    void run_pronounThatFoldsIntoTheLostName_isNotItsSpelling() {
        final List<CheckFinding> found =
                TextChecks.run("Finn stayed home.", "Він залишився вдома.", "en", "uk", List.of("Finn → Фінн"));

        assertThat(found).singleElement().extracting(CheckFinding::kind).isEqualTo(FindingKind.NAME_MISSING);
    }
}

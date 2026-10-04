package ua.bookloom.pipeline.qa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.project.Severity;
import ua.bookloom.pipeline.DisplayText;

/** The length-ratio check: the pair's band, its widening for a short source, and the margin window. */
class LengthCheckTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "a normal Ukrainian target passes,100,115,en,uk,1.0",
        "a target near the band's edge passes with half the margin,200,151,en,uk,0.5",
        "an unlisted pair uses the default band,30,69,uk,el,1.0",
    })
    void run_passWithRepeatedCharacters_matchesExpectedMargin(
            final String name,
            final int sourceChars,
            final int targetChars,
            final String sourceLang,
            final String targetLang,
            final double expectedMargin) {
        final CheckResult result = LengthCheck.run(
                SoftCheckFixtures.length("a".repeat(sourceChars), "b".repeat(targetChars), sourceLang, targetLang));

        assertThat(result.margin()).isCloseTo(expectedMargin, within(1e-9));
        assertThat(result.passed()).isTrue();
        assertThat(result.blocking()).isFalse();
        assertThat(result.finding()).isNull();
    }

    @Test
    void run_shortSourceWidenedBand_passes() {
        final CheckResult result = LengthCheck.run(SoftCheckFixtures.length("Go.", "Йди.", "en", "uk"));

        assertThat(result.margin()).isCloseTo(1.0, within(1e-9));
        assertThat(result.passed()).isTrue();
    }

    // The 14 faithful compact lines of the real run the old band flagged as omissions: 25 to 39 characters, at most
    // five words, the target about half the length and keeping at least half the words.
    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "Unfortunately, nothing happened.|На жаль, нічого.",
                "Whatever happens, happens, sir.|Що буде, те буде.",
                "Nevertheless, everybody listened.|Проте всі слухали.",
                "Unfortunately, impossible.|На жаль, ні.",
                "Understandably, everyone left.|Усі пішли.",
                "Interestingly, nobody noticed.|Ніхто не помітив.",
                "Obviously, you misunderstood.|Ви не зрозуміли.",
                "Sometimes, nothing matters.|Часом байдуже.",
                "Apparently, somebody knocked.|Хтось постукав.",
                "Fortunately, everybody survived.|Усі вціліли.",
                "Thoroughly disgusting, Dudley.|Бридко, Дадлі.",
                "Unfortunately, nobody believed.|Ніхто не повірив.",
                "Eventually, everyone understood.|Усі зрозуміли.",
                "Surprisingly, nothing exploded.|Нічого не сталось.",
                "Gravity keeps the oceans in place|Гравітація утримує океани на місці"
            })
    void run_compactShortLineSaidInAboutHalfTheCharacters_passes(final String source, final String target) {
        final CheckResult result = LengthCheck.run(SoftCheckFixtures.length(source, target, "en", "uk"));

        assertThat(result.passed()).isTrue();
        assertThat(result.blocking()).isFalse();
        assertThat(result.finding()).isNull();
    }

    @Test
    void run_longerLineCutToAFragment_stillFailsAsAnOmission() {
        final CheckResult result = LengthCheck.run(
                SoftCheckFixtures.length("The monster met me at midnight.", "Чудовисько тут", "en", "uk"));

        assertThat(result.passed()).isFalse();
        assertThat(result.finding()).isNotNull();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "a half-length Ukrainian target fails,100,50,en,uk",
        "a short source's widened band still fails,3,14,en,uk"
    })
    void run_failWithRepeatedCharacters_blocksWithMediumOmissionFinding(
            final String name,
            final int sourceChars,
            final int targetChars,
            final String sourceLang,
            final String targetLang) {
        final CheckResult result = LengthCheck.run(
                SoftCheckFixtures.length("a".repeat(sourceChars), "b".repeat(targetChars), sourceLang, targetLang));

        assertThat(result.margin()).isCloseTo(0.0, within(1e-9));
        assertThat(result.passed()).isFalse();
        assertThat(result.blocking()).isTrue();
        assertThat(result.finding()).isNotNull();
        assertThat(result.finding().kind()).isEqualTo("omission");
        assertThat(result.finding().severity()).isEqualTo(Severity.MEDIUM);
        assertThat(result.finding().raisedBy()).isEqualTo("length");
    }

    // A word lost where the model hid it behind a token the repair then dropped: the characters still fit the band,
    // the words or the space left before the punctuation do not.
    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            a name dropped before a full stop | We measured it with the help of the old brass pendulum and Nell. \
            | Ми виміряли це за допомогою старого латунного маятника та .
            a name dropped before a comma | Vance said it slowly, and Nell wrote every word of it down in the log. \
            | Сказала це повільно, і , записала кожне слово в журнал на сторінці.
            half the words gone | When the sun rose over the hill the pendulum was still swinging slowly in the cold. \
            | Коли зійшло сонцеееееееееееее над пагорбоооооооооооом.
            a closing sentence dropped | Don’t trust a single reading, Vance wrote in the margin. Trust the pattern. \
            | Не довіряй жодному показанню, — написала Венс уmargin.
            """)
    void run_wordsMissingFromTheTarget_blocksWithOmissionFinding(
            final String name, final String source, final String target) {
        final CheckResult result = LengthCheck.run(SoftCheckFixtures.length(source, target, "en", "uk"));

        assertThat(result.passed()).isFalse();
        assertThat(result.blocking()).isTrue();
        assertThat(result.finding()).isNotNull();
        assertThat(result.finding().kind()).isEqualTo("omission");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            a faithful translation | Words like heavy and light describe weight, not mass, and Vance never let a student mix them up. \
            | Слова на кшталт «важкий» і «легкий» описують вагу, а не масу, і Венс ніколи не дозволяла студентам їх плутати.
            a space before punctuation the source has too | Wait , he said , and then nothing more came out of his mouth at all. \
            | Зачекай , сказав він , і більше нічого не вийшло з його вуст узагалі.
            a short source is never counted | Mass and weight | Маса і вага
            a verse line Ukrainian says in fewer words | A stone let go will find the ground, \
            | Камінь, відпущений, знайде землю,
            """)
    void run_noWordMissing_passes(final String name, final String source, final String target) {
        final CheckResult result = LengthCheck.run(SoftCheckFixtures.length(source, target, "en", "uk"));

        assertThat(result.passed()).isTrue();
    }

    // A source of protected tokens only display-texts to empty, exactly like a real all-placeholder segment would.
    @Test
    void run_emptySource_skipsMeasuringLength() {
        final CheckResult result =
                LengthCheck.run(SoftCheckFixtures.length(DisplayText.of("⟦g0⟧⟦g1⟧"), "b".repeat(10), "en", "uk"));

        assertThat(result.margin()).isCloseTo(1.0, within(1e-9));
        assertThat(result.skipped()).isTrue();
    }
}

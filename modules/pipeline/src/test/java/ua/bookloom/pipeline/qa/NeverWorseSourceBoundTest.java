package ua.bookloom.pipeline.qa;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.qa.NeverWorseTest.EN;
import static ua.bookloom.pipeline.qa.NeverWorseTest.en;
import static ua.bookloom.pipeline.qa.NeverWorseTest.facts;
import static ua.bookloom.pipeline.qa.NeverWorseTest.findingOf;
import static ua.bookloom.pipeline.qa.NeverWorseTest.marked;

import org.junit.jupiter.api.Test;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.Severity;

/**
 * {@link NeverWorse}'s counted vetoes are bound by the source: a repair that removes what only the current text holds —
 * a wrong number, a stray quote, a doubled sentence — is not refused for it.
 */
class NeverWorseSourceBoundTest {

    @Test
    void judge_fixesReplacedLeadingNumber_keeps() {
        final String source = "3. Night pass";
        final Candidate current = en(source, "5. Нічний перевал");

        final Verdict verdict = NeverWorse.judge(
                current, en(source, "3. Нічний перевал"), marked(source, EN, findingOf(current, "number")));

        assertThat(verdict).isEqualTo(new Verdict.Keep());
    }

    @Test
    void judge_removesStrayQuote_keeps() {
        final String source = "He said go, and he left.";
        final Candidate current = en(source, "Він сказав «іди, і пішов.");

        final Verdict verdict = NeverWorse.judge(current, en(source, "Він сказав іди, і пішов."), facts(source, EN));

        assertThat(verdict).isEqualTo(new Verdict.Keep());
    }

    @Test
    void judge_dropsDuplicatedSentence_keeps() {
        final String source = "He came home.";
        final Candidate current = en(source, "Він прийшов додому. Він прийшов додому.");
        // No check counts a sentence too many, so the reviewer's note is what marked the text.
        final QaFinding doubled = new QaFinding("repetition", Severity.HIGH, "the sentence is doubled", "reviewer");

        final Verdict verdict =
                NeverWorse.judge(current, en(source, "Він прийшов додому."), marked(source, EN, doubled));

        assertThat(verdict).isEqualTo(new Verdict.Keep());
    }
}

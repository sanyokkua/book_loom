package ua.bookloom.document.inspect;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.LanguageEvidence;

/**
 * {@link LanguageEvidenceReader#evaluate}'s normalization, majority and verdict computation, over the raw declared
 * code and content-document root declarations each format's reader supplies.
 */
class LanguageEvidenceReaderTest {

    // WHEN a package declares one language and more than half of its content documents declare a
    // different one, THEN the verdict is MISMATCH and the majority is recorded.
    @Test
    void evaluate_packageEnglishAndMajorityUkrainianContent_reportsMismatch() {
        final LanguageEvidence evidence = LanguageEvidenceReader.evaluate("en", Collections.nCopies(24, "uk"));

        assertThat(evidence.declaredRaw()).isEqualTo("en");
        assertThat(evidence.declared()).isEqualTo("en");
        assertThat(evidence.contentMajority()).isEqualTo("uk");
        assertThat(evidence.verdict()).isEqualTo(LanguageEvidence.Verdict.MISMATCH);
    }

    // WHEN a regional package tag and its content majority normalize to the same catalogued
    // language, THEN the verdict is MATCH.
    @Test
    void evaluate_regionalPackageTagAndMatchingContentMajority_reportsMatch() {
        final LanguageEvidence evidence = LanguageEvidenceReader.evaluate("en-US", Collections.nCopies(5, "en"));

        assertThat(evidence.declared()).isEqualTo("en");
        assertThat(evidence.contentMajority()).isEqualTo("en");
        assertThat(evidence.verdict()).isEqualTo(LanguageEvidence.Verdict.MATCH);
    }

    // WHEN no language is declared by more than half of the documents that declare one, THEN no
    // content majority is recorded and the verdict defaults to MATCH rather than a claim it cannot support.
    @Test
    void evaluate_tiedContentDeclarations_reportsNoMajorityAndMatch() {
        final LanguageEvidence evidence = LanguageEvidenceReader.evaluate("en", List.of("uk", "uk", "en", "en"));

        assertThat(evidence.contentMajority()).isNull();
        assertThat(evidence.verdict()).isEqualTo(LanguageEvidence.Verdict.MATCH);
    }

    // WHEN no content document declares any language, THEN there is no majority and the verdict is
    // MATCH.
    @Test
    void evaluate_noContentDeclarations_reportsNoMajorityAndMatch() {
        final LanguageEvidence evidence = LanguageEvidenceReader.evaluate("fr", List.of());

        assertThat(evidence.contentMajority()).isNull();
        assertThat(evidence.verdict()).isEqualTo(LanguageEvidence.Verdict.MATCH);
    }

    // WHEN the package declares no language but its content documents agree on one, THEN the
    // majority is recorded and the verdict is ABSENT — the book declares nothing, whatever the content shows.
    @Test
    void evaluate_noPackageLanguageWithContentMajority_reportsAbsentWithMajority() {
        final LanguageEvidence evidence = LanguageEvidenceReader.evaluate(null, Collections.nCopies(3, "de"));

        assertThat(evidence.declaredRaw()).isNull();
        assertThat(evidence.declared()).isNull();
        assertThat(evidence.contentMajority()).isEqualTo("de");
        assertThat(evidence.verdict()).isEqualTo(LanguageEvidence.Verdict.ABSENT);
    }

    // an FB2 book's <lang>ua</lang> normalizes to the catalogued Ukrainian tag, and with no
    // per-document content declarations to compare it against the verdict is MATCH.
    @Test
    void evaluate_fb2RetiredUkrainianTagAndNoContentDeclarations_normalizesAndReportsMatch() {
        final LanguageEvidence evidence = LanguageEvidenceReader.evaluate("ua", List.of());

        assertThat(evidence.declaredRaw()).isEqualTo("ua");
        assertThat(evidence.declared()).isEqualTo("uk");
        assertThat(evidence.contentMajority()).isNull();
        assertThat(evidence.verdict()).isEqualTo(LanguageEvidence.Verdict.MATCH);
    }

    // a language outside the 34-language catalogue that the JDK can name is recognized, not UNRECOGNIZED.
    @Test
    void evaluate_uncataloguedButNameableTag_reportsMatch() {
        final LanguageEvidence evidence = LanguageEvidenceReader.evaluate("la", List.of());

        assertThat(evidence.declaredRaw()).isEqualTo("la");
        assertThat(evidence.declared()).isEqualTo("la");
        assertThat(evidence.contentMajority()).isNull();
        assertThat(evidence.verdict()).isEqualTo(LanguageEvidence.Verdict.MATCH);
    }

    // a Markdown frontmatter lang value that no language names is UNRECOGNIZED, not
    // ABSENT — it was declared, just not to a language this system knows.
    @Test
    void evaluate_markdownTagNoLanguageNames_reportsUnrecognized() {
        final LanguageEvidence evidence = LanguageEvidenceReader.evaluate("xx-yy", List.of());

        assertThat(evidence.declaredRaw()).isEqualTo("xx-yy");
        assertThat(evidence.declared()).isNull();
        assertThat(evidence.verdict()).isEqualTo(LanguageEvidence.Verdict.UNRECOGNIZED);
    }

    // TXT carries no declaration of any kind, so the evidence is ABSENT with nothing else filled in.
    @Test
    void evaluate_txtWithNoDeclaration_reportsAbsent() {
        final LanguageEvidence evidence = LanguageEvidenceReader.evaluate(null, List.of());

        assertThat(evidence.declaredRaw()).isNull();
        assertThat(evidence.declared()).isNull();
        assertThat(evidence.contentMajority()).isNull();
        assertThat(evidence.verdict()).isEqualTo(LanguageEvidence.Verdict.ABSENT);
    }
}

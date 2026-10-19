package ua.bookloom.pipeline.checks;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The Latin-run check and the kept terms that exempt a run from it (15j.J1). */
class TextChecksLatinRunTest {

    // IF a name the glossary keeps in Latin counted as a left-over run, THEN Keep original could never pass. Only the
    // kept terms the caller names are left alone (15j.J1): a pair whose rendering is Latin is no exemption by itself.
    @Test
    void run_latinRunThatIsAKeptTerm_isNotALatinRun() {
        final List<CheckFinding> found = TextChecks.run(
                "Danny Vosk sat at the bar.",
                "Danny Vosk сидів біля стійки.",
                "en",
                "uk",
                List.of("Danny Vosk → Danny Vosk"),
                List.of("Danny Vosk"));

        assertThat(found).extracting(CheckFinding::kind).doesNotContain(FindingKind.LATIN_RUN);
    }

    @Test
    void run_latinRunThatIsOnlyAGlossaryPairNotKept_isStillALatinRun() {
        final List<CheckFinding> found = TextChecks.run(
                "Danny Vosk sat at the bar.",
                "Danny Vosk сидів біля стійки.",
                "en",
                "uk",
                List.of("Danny Vosk → Danny Vosk"),
                List.of());

        assertThat(found)
                .filteredOn(finding -> finding.kind() == FindingKind.LATIN_RUN)
                .extracting(finding -> finding.span().text())
                .containsExactly("Danny Vosk");
    }

    @Test
    void run_latinRunBesideAKeptTerm_isStillALatinRun() {
        final List<CheckFinding> found = TextChecks.run(
                "Danny Vosk read the Daily Courier on the train.",
                "Danny Vosk читав Daily Courier у нічному поїзді.",
                "en",
                "uk",
                List.of("Danny Vosk → Danny Vosk"),
                List.of("Danny Vosk"));

        assertThat(found)
                .filteredOn(finding -> finding.kind() == FindingKind.LATIN_RUN)
                .extracting(finding -> finding.span().text())
                .containsExactly("Daily Courier");
    }

    // IF an identifier in a target were a Latin run, THEN a part number could never stay as written.
    @Test
    void run_identifierInATransliterateTarget_isNotALatinRun() {
        final List<CheckFinding> found = TextChecks.run(
                "Rod KX-7 and rod ZX-81 hung there.",
                "Стрижень KX-7 і стрижень ZX-81 висіли там.",
                "en",
                "uk",
                List.of(),
                List.of());

        assertThat(found).extracting(CheckFinding::kind).doesNotContain(FindingKind.LATIN_RUN);
    }
}

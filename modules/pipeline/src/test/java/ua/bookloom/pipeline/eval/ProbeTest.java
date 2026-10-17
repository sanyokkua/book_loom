package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The single-case probe, offline: it builds the production requests and never needs a real model. */
class ProbeTest {

    /** What one probe run printed and returned. */
    private record Ran(int exit, String out, String err) {}

    private static Ran probe(final String... args) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final ByteArrayOutputStream err = new ByteArrayOutputStream();
        final int exit = Probe.run(
                args,
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Ran(exit, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "dialogue-locked-name",
                "fix-echo",
                "repair-placeholder",
                "review-door",
                "suggest",
                "garbled-word",
                "quotes-stray-close-1",
                "batch-terms-protocol",
                "reviewer-glossary-6",
                "repair-dropped-close",
                "injection-notes-uk"
            })
    void dry_anyCaseKind_printsTheRequestAndMakesNoCall(final String id) {
        final Ran ran = probe("--dry", "--case", id);

        assertThat(ran.exit()).as(ran.err()).isZero();
        assertThat(ran.out()).contains("[system]", "[user]", "num_ctx=", "(dry: no call made)");
    }

    @ParameterizedTest
    @ValueSource(strings = {"dialogue-locked-name"})
    void dry_draftCase_splitsTheMessagesBySectionAndShowsTheSource(final String id) {
        final Ran ran = probe("--dry", "--case", id, "--window", "8192");

        assertThat(ran.out())
                .contains("num_ctx=8192", "-- section 1", "said Edwin", "format=")
                .contains("DRAFT");
    }

    @ParameterizedTest
    @ValueSource(strings = {"dialogue-locked-name", "review-door", "quotes-stray-close-1"})
    void pseudo_caseWithAModel_printsTheReplyTheVerdictAndTheUsage(final String id) {
        final Ran ran = probe("--provider", "pseudo", "--case", id);

        assertThat(ran.exit()).as(ran.err()).isZero();
        assertThat(ran.out()).contains("[reply]", "[verdict]", "[usage]", "wallMs=");
    }

    @ParameterizedTest
    @ValueSource(strings = {"dialogue-locked-nam", "nope-nothing"})
    void case_unknownId_failsAndNamesTheClosestIds(final String id) {
        final Ran ran = probe("--dry", "--case", id);

        assertThat(ran.exit()).isEqualTo(2);
        assertThat(ran.err()).contains("unknown case '" + id + "'", "closest:");
    }

    @Test
    void case_nearMissOfAnId_listsTheRealIdFirst() {
        final Ran ran = probe("--dry", "--case", "dialogue-locked-nam");

        assertThat(ran.err()).contains("closest: dialogue-locked-name");
    }

    @ParameterizedTest
    @ValueSource(strings = {"prompt", "defects", "realrun"})
    void list_aSuite_namesItsCases(final String suite) {
        final Ran ran = probe("--list", suite);

        assertThat(ran.exit()).isZero();
        assertThat(ran.out()).contains("[" + suite + "]");
    }

    @ParameterizedTest
    @ValueSource(strings = {"--repeat", "--window"})
    void args_badNumber_isAUsageError(final String flag) {
        final Ran ran = probe("--dry", "--case", "plain-short", flag, "many");

        assertThat(ran.exit()).isEqualTo(2);
        assertThat(ran.err()).contains(flag);
    }
}

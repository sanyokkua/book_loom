package ua.bookloom.pipeline.checks;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Identifiers (UUID, hex, ISBN, URL, e-mail) are kept verbatim and exempt from the language and script checks. */
class IdentifierChecksTest {

    private static List<CheckFinding> uk(final String source, final String target) {
        return TextChecks.run(source, target, "en", "uk");
    }

    private static final String UUID = "3f2b8c1d-9a4e-4b7a-8c11-2d5f6e7a8b90";

    @ParameterizedTest
    @ValueSource(
            strings = {
                UUID,
                "9d8c7b6a5e4f",
                "https://example.org/a-b?c=d",
                "reader@example.org",
                "ISBN 978-0-306-40615-7",
            })
    void run_identifierKeptVerbatim_isNotHeldAgainstTheTranslation(final String identifier) {
        final String source = "Note this reference " + identifier + " in the margin of the old ledger today.";
        final String target = "Занотуйте цей запис " + identifier + " на берегу старої книги сьогодні.";

        assertThat(uk(source, target)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {UUID, "https://example.org/a-b?c=d", "reader@example.org", "9d8c7b6a5e4f"})
    void run_identifierChangedByTheTranslation_isBlockingAndNamesIt(final String identifier) {
        final String source = "Note this reference " + identifier + " in the margin of the old ledger today.";
        final String target = "Занотуйте цей запис ідентифікатор на берегу старої книги сьогодні.";

        assertThat(uk(source, target))
                .filteredOn(finding -> finding.kind() == FindingKind.IDENTIFIER_CHANGED)
                .singleElement()
                .satisfies(finding -> {
                    assertThat(finding.blocking()).isTrue();
                    assertThat(finding.explanation()).contains(identifier);
                });
    }

    @Test
    void run_identifierBeforeATrailingFullStop_isKeptWithoutThePunctuation() {
        final String source = "The ledger lives at https://example.org/a-b.";
        final String target = "Книга лежить за адресою https://example.org/a-b.";

        assertThat(uk(source, target)).isEmpty();
    }
}

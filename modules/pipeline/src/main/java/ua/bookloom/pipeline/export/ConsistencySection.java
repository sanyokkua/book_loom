package ua.bookloom.pipeline.export;

import java.util.List;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.pipeline.GenderWait;
import ua.bookloom.api.pipeline.ProofreadSummary;

/**
 * The report's proofread section: how many segments the Proofread stage re-read, how many still wait on the person,
 * and each character whose gender holds segments back — counted from the stores, since the export calls no model.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ConsistencySection {

    /**
     * Words the proofread summary.
     *
     * @param summary the non-null summary read from the stores
     * @return Markdown lines: the two counts, then a line per kind of gender wait when one holds a segment back
     */
    static String of(final ProofreadSummary summary) {
        final StringBuilder text = new StringBuilder()
                .append("- Re-read by the Proofread stage: ")
                .append(summary.reRead())
                .append('\n')
                .append("- Still waiting on you: ")
                .append(summary.waiting())
                .append('\n');
        text.append(waits("- Waiting on a gender: ", summary.awaitingGender(), false));
        text.append(waits("- Waiting on a suggested gender to confirm: ", summary.awaitingGender(), true));
        return text.toString();
    }

    private static String waits(final String label, final List<GenderWait> waits, final boolean suggested) {
        final String named = waits.stream()
                .filter(wait -> wait.suggested() == suggested)
                .map(wait -> wait.character() + " " + wait.segments())
                .collect(Collectors.joining(", "));
        return named.isEmpty() ? "" : label + named + "\n";
    }
}

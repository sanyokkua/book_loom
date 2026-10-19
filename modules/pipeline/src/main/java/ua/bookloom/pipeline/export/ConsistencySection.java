package ua.bookloom.pipeline.export;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.ConsistencyChecks;
import ua.bookloom.pipeline.revision.ConsistencyReport;

/** The report's consistency-pass section: what the model steps tried, kept and refused, and each change by locator. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ConsistencySection {

    /**
     * Words the pass's outcome.
     *
     * @param pass what the pass did, or null when it was not run
     * @return Markdown lines; a sentence when the pass did not run or changed and tried nothing
     */
    static String of(@Nullable final ConsistencyReport pass) {
        if (pass == null) {
            return "The consistency pass was not run.\n";
        }
        final List<String> lines = new ArrayList<>(counts(pass.checks(), pass.neighbourFixes()));
        pass.notes().stream().map(note -> "- " + note).forEach(lines::add);
        return lines.isEmpty()
                ? "The consistency pass changed nothing.\n"
                : lines.stream().map(line -> line + "\n").collect(Collectors.joining());
    }

    private static List<String> counts(final ConsistencyChecks checks, final int neighbourFixes) {
        final List<String> lines = new ArrayList<>();
        if (checks.retriedImproved() > 0) {
            lines.add("- Drafted again and replaced: " + checks.retriedImproved());
        }
        if (checks.retriedKept() > 0) {
            lines.add("- Drafted again, old text kept: " + checks.retriedKept());
        }
        if (neighbourFixes + checks.neighbourUnchanged() > 0) {
            lines.add("- Neighbour check: fixed " + neighbourFixes + ", unchanged " + checks.neighbourUnchanged());
        }
        if (checks.refusedTotal() > 0) {
            lines.add("- Answers refused: " + checks.refusedTotal() + " (" + rules(checks.refused()) + ")");
        }
        if (checks.skipped() > 0) {
            lines.add("- Skipped, the call failed: " + checks.skipped());
        }
        return lines;
    }

    private static String rules(final Map<String, Integer> refused) {
        return refused.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(rule -> rule.getKey() + " " + rule.getValue())
                .collect(Collectors.joining(", "));
    }
}

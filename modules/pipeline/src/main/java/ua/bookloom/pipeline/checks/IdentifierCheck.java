package ua.bookloom.pipeline.checks;

import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Every identifier the source writes ({@link Identifiers}) must stand in the target exactly as written. */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class IdentifierCheck {

    static List<CheckFinding> find(final String source, final String target) {
        final List<CheckFinding> findings = Identifiers.in(source).stream()
                .distinct()
                .filter(identifier -> !target.contains(identifier))
                .map(identifier -> new CheckFinding(
                        FindingKind.IDENTIFIER_CHANGED,
                        new TextSpan(0, 0, ""),
                        "The source has " + identifier + " and the translation does not: copy it exactly as the"
                                + " source writes it, and do not translate it.",
                        true))
                .toList();
        if (!findings.isEmpty()) {
            log.debug("Identifier check: {} identifier(s) changed or lost", findings.size());
        }
        return findings;
    }
}

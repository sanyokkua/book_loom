package ua.bookloom.ui.state;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.RaiserWords;

/**
 * The note over a model's refused reply, saying why it was refused. A reply is refused for any blocking finding, not
 * only for broken formatting, so the note follows the findings: lost formatting marks first (the reply cannot be
 * written back as it is), then letters of another alphabet, then a wrong language as {@link FindingBadge} words it,
 * else the findings listed under the desk's "What may be wrong" tab.
 */
@Slf4j
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class RejectedReplyNote {

    /** The finding kind of the formatting-mark and reply-shape checks. */
    private static final String MARKUP = "markup";

    /** The checks that look at the letters a reply is written in rather than at its words. */
    private static final Set<String> SCRIPT_CHECKS = Set.of("alphabet", "script", "script-purity", "latin-run");

    static MessageKey of(final List<QaFinding> findings) {
        Objects.requireNonNull(findings, "findings");
        final MessageKey note;
        if (findings.stream().anyMatch(finding -> MARKUP.equals(finding.kind().toLowerCase(Locale.ROOT)))) {
            note = MessageKey.REVIEW_TARGET_REJECTED;
        } else if (findings.stream()
                .anyMatch(finding -> SCRIPT_CHECKS.contains(RaiserWords.checkOf(finding.raisedBy())))) {
            note = MessageKey.REVIEW_TARGET_REJECTED_SCRIPT;
        } else if (FindingBadge.of(findings, null).equals(Optional.of(FindingBadge.WRONG_LANGUAGE))) {
            note = MessageKey.REVIEW_TARGET_REJECTED_LANGUAGE;
        } else {
            note = MessageKey.REVIEW_TARGET_REJECTED_CHECKS;
        }
        log.debug("refused reply with {} finding(s) noted as {}", findings.size(), note);
        return note;
    }
}

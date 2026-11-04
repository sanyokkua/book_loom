package ua.bookloom.ui.i18n;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Words for who raised a finding. The final audit stores its findings as {@code audit:} and the check's name, so the
 * prefix is cut here, once, and the same words serve the run's own checks and the audit's.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RaiserWords {

    // The audit's own prefix (AuditFindings.PREFIX); :pipeline exports no audit package to :ui.
    /** The prefix of an audit finding's {@code raisedBy}. */
    public static final String AUDIT_PREFIX = "audit:";

    /**
     * The check a finding's {@code raisedBy} names, with the audit's prefix cut off.
     *
     * @param raisedBy the non-null {@code raisedBy} of a finding
     * @return the check's name as the run or the audit reports it
     */
    public static String checkOf(final String raisedBy) {
        Objects.requireNonNull(raisedBy, "raisedBy");
        return raisedBy.startsWith(AUDIT_PREFIX) ? raisedBy.substring(AUDIT_PREFIX.length()) : raisedBy;
    }

    /**
     * Who raised a finding, in the window's language.
     *
     * @param messages the non-null catalogue
     * @param raisedBy the non-null {@code raisedBy} of a finding
     * @return for example "the negation check"; "a check" for a name no catalogue line knows
     */
    public static String raiser(final Messages messages, final String raisedBy) {
        Objects.requireNonNull(messages, "messages");
        return messages.code(MessageKey.REVIEW_RAISER, raisedBy);
    }

    /**
     * The short noun a "Worth a second look" row shows for the check that doubts it.
     *
     * @param messages the non-null catalogue
     * @param raisedBy the non-null {@code raisedBy} of an audit finding
     * @return for example "negation"; "a doubt" for a name no catalogue line knows
     */
    public static String doubt(final Messages messages, final String raisedBy) {
        Objects.requireNonNull(messages, "messages");
        return messages.code(MessageKey.REVIEW_DOUBT, raisedBy);
    }
}

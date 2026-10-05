package ua.bookloom.api.project;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A segment decision deferred for a later resolution — a name awaiting the person's decision, a gender awaiting
 * resolution, or a locked rendering changed after decided segments used
 * the old one ({@code specs/translation-pipeline/spec.md} "Record deferrals and revise backwards on Max").
 *
 * @param id the deferral's stable id
 * @param projectId the owning project's id
 * @param segmentId the affected segment's id
 * @param reason why this deferral was recorded
 * @param waitingOn what this deferral is waiting on (e.g. an unresolved name), or null when not applicable
 * @param replacedRendering the rendering a locked-term sweep replaces, or null when not applicable
 * @param proposal the plain-text proposal shown for a user-edited segment, or null when not applicable
 * @param maskedProposal the masked-text proposal shown for a user-edited segment, or null when not applicable
 */
public record Deferral(
        String id,
        String projectId,
        String segmentId,
        DeferralReason reason,
        @Nullable String waitingOn,
        @Nullable String replacedRendering,
        @Nullable String proposal,
        @Nullable String maskedProposal) {

    /**
     * Validates the invariants a caller is entitled to assume.
     */
    public Deferral {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(segmentId, "segmentId");
        Objects.requireNonNull(reason, "reason");
    }
}

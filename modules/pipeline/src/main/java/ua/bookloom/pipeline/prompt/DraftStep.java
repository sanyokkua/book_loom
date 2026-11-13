package ua.bookloom.pipeline.prompt;

import ua.bookloom.api.pipeline.CallKind;

/**
 * The three calls of a segment's draft, each one call kind. It is narrower than {@link CallKind} so the
 * translator's exhaustive switch over it never grows when another call (pre-scan, summary, revision) is added.
 */
public enum DraftStep {

    /** The first translation attempt. */
    DRAFT(CallKind.DRAFT),

    /** The correction after a reply is not the required JSON object. */
    STRUCTURAL_REPAIR(CallKind.STRUCTURAL_REPAIR),

    /** The one repair of a draft that kept a blocking or repair-level finding, its defects named. */
    REPAIR(CallKind.REPAIR);

    private final CallKind callKind;

    DraftStep(final CallKind callKind) {
        this.callKind = callKind;
    }

    /** The kind a model call for this step is announced and routed under. */
    public CallKind callKind() {
        return callKind;
    }
}

package ua.bookloom.pipeline.prompt;

import ua.bookloom.api.pipeline.CallKind;

/**
 * The three calls of the draft step, each one prompt of the catalogue. It is narrower than {@link PromptName} so the
 * translator's exhaustive switch over it never grows when another prompt (pre-scan, summary, revision) is added.
 */
public enum DraftStep {

    /** The first translation attempt. */
    DRAFT(PromptName.DRAFT, CallKind.DRAFT),

    /** The correction after a reply is not the required JSON object. */
    STRUCTURAL_REPAIR(PromptName.STRUCTURAL_REPAIR, CallKind.STRUCTURAL_REPAIR),

    /** The correction after a target fails the placeholder gate. */
    PLACEHOLDER_REPAIR(PromptName.PLACEHOLDER_REPAIR, CallKind.PLACEHOLDER_REPAIR);

    private final PromptName promptName;
    private final CallKind callKind;

    DraftStep(final PromptName promptName, final CallKind callKind) {
        this.promptName = promptName;
        this.callKind = callKind;
    }

    /** The catalogue prompt this step renders. */
    public PromptName promptName() {
        return promptName;
    }

    /** The kind a model call for this step is announced and routed under. */
    public CallKind callKind() {
        return callKind;
    }
}

package ua.bookloom.api.llm;

/**
 * What the person chose on Settings › Models for one model's thinking; stored on its {@link ModelSettings}. It wins over
 * the model's profile, except that a control the model or its server does not declare is never sent.
 */
public enum ThinkingOverride {
    /** The profile decides per call kind: thinking off for every call kind BookLoom makes today. */
    RECOMMENDED,
    /** Thinking stays off for drafts and repairs, and is allowed for the proofread and the names pass. */
    ALLOW_PROOFREAD_AND_NAMES,
    /** Thinking on for every call kind. */
    ALWAYS
}

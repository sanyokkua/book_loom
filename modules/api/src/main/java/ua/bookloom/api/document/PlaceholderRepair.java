package ua.bookloom.api.document;

/**
 * How far a deterministic placeholder repair may move the model's own markup, from the least to the most invasive.
 * Both keep every word of the reply; neither calls a model.
 */
public enum PlaceholderRepair {

    /**
     * Keeps every token the reply placed, drops a token the source does not hold or holds fewer times, and puts each
     * missing token back at the word boundary nearest its proportional source position.
     */
    RESTORE_MISSING,

    /**
     * Strips every token from the reply and places all of them again, in source order, each at the word boundary
     * nearest its proportional source position — the fallback when the model's own placement cannot be kept.
     */
    REWRAP_ALL
}

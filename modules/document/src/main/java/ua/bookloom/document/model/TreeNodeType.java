package ua.bookloom.document.model;

/**
 * What a {@link TreeNode} is, independent of what it is called — the first axis the masking classifier tests
 * (ADR-0031, design.md D2).
 *
 * <p>Four constants and no more. A comment, a processing instruction and an entity reference all mask to one
 * atomic token carrying their own {@link TreeNode#markup()}, so the classifier never needs to tell them apart —
 * they all fall to {@link #OTHER}. Only {@link #CDATA} needs separating from {@link #TEXT} (a CDATA section
 * subclasses the text class in both jsoup and JDOM2, so an {@code instanceof} check on the wrong order silently
 * decodes it), and {@link #ELEMENT} from everything else.
 */
public enum TreeNodeType {

    /** An element — masked by name, then by whether it has children (D2). */
    ELEMENT,

    /** Ordinary character data — its decoded text is translatable prose. */
    TEXT,

    /**
     * A CDATA section. Reported separately from {@link #TEXT} because both jsoup's {@code CDataNode} and JDOM2's
     * {@code CDATA} are subclasses of their library's text type; testing {@link #TEXT} first would decode a CDATA
     * section into ordinary text and re-escape it on restore, destroying the CDATA form.
     */
    CDATA,

    /** A comment, a processing instruction, or an entity reference — none of it is character data. */
    OTHER
}

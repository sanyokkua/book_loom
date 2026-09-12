package ua.bookloom.document.model;

/**
 * Which tree-shaped format a {@link TreeNode} was parsed from — passed explicitly to
 * {@link BlockSegmentWalker#walk(TreeNode, String, TreeDialect)} and {@link TreeMasker} rather than derived from
 * the adapter, because part of the named-atomic element set — the {@code code} name alone, the rest applying to
 * both formats — and the {@code pre} line-feed rule differ by format and both production call sites already know which one they are (design.md D2, D11; ADR-0031).
 */
public enum TreeDialect {

    /** EPUB spine content — jsoup-parsed XHTML. */
    XHTML,

    /** FictionBook 2 — JDOM2-parsed strict XML. */
    FICTION_BOOK
}

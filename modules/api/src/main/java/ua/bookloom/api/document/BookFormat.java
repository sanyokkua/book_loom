package ua.bookloom.api.document;

/**
 * The book formats {@code :document} can parse and re-emit.
 *
 * <p>Export is same-format-only (ADR-0004, DD-30): a {@code Document} parsed as {@link #EPUB} is written back as
 * EPUB, never converted to another format. {@code add-document-skeleton-and-epub-roundtrip} covered {@link #EPUB}
 * alone; {@code add-fb2-md-txt-roundtrip} added {@link #FB2}, {@link #MARKDOWN} and {@link #TXT}, so this enum is
 * now closed over every format the importer supports.
 *
 * <p>The enum being closed is load-bearing rather than incidental: {@code DocumentService} dispatches on it with
 * an exhaustive {@code switch}, so adding a constant here without adding its reader/writer branch is a compile
 * error rather than a {@code null} a user discovers.
 */
public enum BookFormat {

    /** Electronic Publication — a zip container of XHTML content documents plus an OPF package document. */
    EPUB,

    /** FictionBook 2 — a single XML document. */
    FB2,

    /** CommonMark Markdown, optionally with a frontmatter block. */
    MARKDOWN,

    /** Plain text. */
    TXT
}

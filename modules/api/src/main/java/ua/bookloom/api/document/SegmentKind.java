package ua.bookloom.api.document;

/**
 * What kind of block a {@link Segment} was parsed from.
 *
 * <p>Two groups. The <strong>body kinds</strong> ({@link #PARAGRAPH} through {@link #TITLE}) come from ordinary
 * block-level content inside a unit. The <strong>metadata-unit kinds</strong> ({@link #METADATA_TITLE} through
 * {@link #NAV_LABEL}) come from a document's synthetic metadata unit — book title/author, Markdown frontmatter
 * values, image alt text, and nav/NCX table-of-contents labels
 * ({@code 02_Architecture/03_DOCUMENT_MODEL.md#metadata-unit}).
 *
 * <p>Every constant ships now even though {@code add-document-skeleton-and-epub-roundtrip} only ever produces
 * {@link #PARAGRAPH}, {@link #HEADING} and {@link #LIST_ITEM} — seam F1 is a fixed target for the changes that
 * follow, so this enum does not grow as each later producer arrives (DD-07,
 * {@code 01_MODULE_INVENTORY.md#module-api}).
 */
public enum SegmentKind {

    /** A prose paragraph. */
    PARAGRAPH,

    /** A section/chapter heading. */
    HEADING,

    /** A single line of verse. */
    VERSE_LINE,

    /** A list item. */
    LIST_ITEM,

    /** A table cell. */
    TABLE_CELL,

    /** A footnote's body. */
    FOOTNOTE,

    /** A figure/table caption. */
    CAPTION,

    /** A title outside the metadata unit — for example a running-head or a title-page heading. */
    TITLE,

    /** The book's title, from OPF {@code dc:title} (EPUB) or {@code title-info/book-title} (FB2). */
    METADATA_TITLE,

    /** The book's author, from OPF {@code dc:creator} (EPUB) or {@code title-info/author} (FB2). */
    METADATA_AUTHOR,

    /** A Markdown frontmatter value; the frontmatter key is never masked or translated. */
    FRONTMATTER_VALUE,

    /** An image's {@code alt} text or caption. */
    ALT,

    /** An EPUB 3 nav-document link label or an EPUB 2 NCX {@code navLabel/text}. */
    NAV_LABEL
}

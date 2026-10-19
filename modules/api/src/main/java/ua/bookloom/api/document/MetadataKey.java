package ua.bookloom.api.document;

/**
 * The keys of {@link Document#metadata()} that more than one module reads or writes.
 *
 * <p>A parsed book has no title or author component: both live in a general-purpose string map, so a key spelled once
 * in a reader and again in the window is a row that silently vanishes the day either is edited. Naming them here makes
 * the readers and the import screen agree by construction.
 */
public enum MetadataKey {

    /** The book's title, as the EPUB and FB2 readers write it and as a Markdown frontmatter {@code title:} line supplies it. */
    TITLE("title"),

    /** The book's author, as the EPUB and FB2 readers write it; a Markdown or plain-text book never supplies one. */
    AUTHOR("author"),

    /**
     * The year the book was written or published: the first four-digit year of the EPUB package's first {@code dc:date},
     * FB2 {@code title-info/date} (else {@code publish-info/year}) or a Markdown frontmatter {@code date:} line. Always a
     * bare year, never a full date; absent when the source holds no four-digit year.
     */
    DATE("date"),

    /** The publisher, as an EPUB {@code dc:publisher}, an FB2 {@code publish-info/publisher} or a frontmatter {@code publisher:} line. */
    PUBLISHER("publisher");

    private final String key;

    MetadataKey(final String key) {
        this.key = key;
    }

    /**
     * The name this entry has in {@link Document#metadata()}.
     *
     * @return the map key
     */
    public String key() {
        return key;
    }
}

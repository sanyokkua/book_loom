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
    AUTHOR("author");

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

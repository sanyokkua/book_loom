package ua.bookloom.api.document;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
    EPUB(".epub"),

    /** FictionBook 2 — a single XML document. */
    FB2(".fb2.zip", ".fb2"),

    /** CommonMark Markdown, optionally with a frontmatter block. */
    MARKDOWN(".markdown", ".md"),

    /** Plain text. */
    TXT(".txt");

    @SuppressWarnings("ImmutableEnumChecker") // List.of creates the immutable suffix list required by this contract.
    private final List<String> suffixes;

    BookFormat(final String... suffixes) {
        this.suffixes = List.of(suffixes);
    }

    /**
     * Returns this format's immutable suffixes in the order used for matching.
     *
     * @return the suffixes, longest first where a format has overlapping suffixes
     */
    public List<String> suffixes() {
        return suffixes;
    }

    /**
     * Resolves a filename by its case-insensitive supported suffix.
     *
     * @param fileName the non-null filename to inspect
     * @return the matching format, or empty when no supported suffix matches
     */
    public static Optional<BookFormat> ofFileName(final String fileName) {
        Objects.requireNonNull(fileName, "fileName");
        for (final BookFormat format : values()) {
            if (format.matches(fileName)) {
                return Optional.of(format);
            }
        }
        return Optional.empty();
    }

    /**
     * Finds the suffix matched by this format while retaining the filename's original casing.
     *
     * @param fileName the non-null filename to inspect
     * @return the matching suffix as it appears in {@code fileName}
     * @throws IllegalArgumentException when this format does not match the filename
     */
    public String matchedSuffix(final String fileName) {
        Objects.requireNonNull(fileName, "fileName");
        for (final String suffix : suffixes) {
            if (hasSuffix(fileName, suffix)) {
                return fileName.substring(fileName.length() - suffix.length());
            }
        }
        throw new IllegalArgumentException("filename does not match " + name() + ": " + fileName);
    }

    private boolean matches(final String fileName) {
        return suffixes.stream().anyMatch(suffix -> hasSuffix(fileName, suffix));
    }

    private static boolean hasSuffix(final String fileName, final String suffix) {
        final int start = fileName.length() - suffix.length();
        return start >= 0 && fileName.regionMatches(true, start, suffix, 0, suffix.length());
    }
}

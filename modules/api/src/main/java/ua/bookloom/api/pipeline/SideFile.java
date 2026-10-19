package ua.bookloom.api.pipeline;

import java.nio.file.Path;
import java.util.Objects;
import ua.bookloom.api.document.BookFormat;

/**
 * A side file an export can write beside the translated book.
 */
public enum SideFile {

    /** The project's glossary in the columns Names &amp; style imports, so a sequel can load it back. */
    GLOSSARY_CSV(".glossary.csv"),

    /** A self-contained source/target table, one row per segment, for checking the translation offline. */
    BILINGUAL_HTML(".bilingual.html"),

    /** The segment counts, the flagged segments with their findings and the consistency notes. */
    QUALITY_REPORT(".report.md");

    private final String suffix;

    SideFile(final String suffix) {
        this.suffix = suffix;
    }

    /**
     * Returns the ending this side file's name carries after the book's name.
     *
     * @return the suffix, such as {@code .glossary.csv}; never null
     */
    public String suffix() {
        return suffix;
    }

    /**
     * Names this side file beside a written book. It is the one naming rule: the export's occupied-path check, the
     * writer of the side files and the Export screen all call it, so the path a person is warned about is the path
     * that is written. The whole format suffix is removed — {@code Kobzar.uk.fb2.zip} gives {@code Kobzar.uk}, never
     * {@code Kobzar.uk.fb2} — in any letter case.
     *
     * @param destination the non-null path the book is written to; it must name a file
     * @param format the non-null format the book is written in
     * @return the side file's path in the destination's folder; a file name the format does not match keeps its
     *     whole name before the side file's suffix
     */
    public Path pathBeside(final Path destination, final BookFormat format) {
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(format, "format");
        final String fileName = Objects.requireNonNull(destination.getFileName(), "destination file name")
                .toString();
        final String name = BookFormat.ofFileName(fileName)
                .filter(format::equals)
                .map(matched -> fileName.substring(
                        0, fileName.length() - matched.matchedSuffix(fileName).length()))
                .orElse(fileName);
        return destination.resolveSibling(name + suffix);
    }
}

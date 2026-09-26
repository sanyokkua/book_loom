package ua.bookloom.util.paths;

import java.nio.file.Path;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.document.BookFormat;

/**
 * Decides where a translated book is written, so the command line and the book-brief screen name the output
 * identically.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DestinationPath {

    /**
     * Places the target language before the format's real suffix, in the source's own directory. The suffix is
     * taken from {@link BookFormat#matchedSuffix(String)} rather than the last dot, because a composite suffix
     * such as {@code .fb2.zip} must stay whole ({@code Kobzar.uk.fb2.zip}, never {@code Kobzar.fb2.uk.zip}).
     *
     * @param source the source book path; its file name must end with a suffix of {@code format}
     * @param format the format the source was recognised as
     * @param targetLanguage the language tag inserted into the file name
     * @return the destination path, relative when {@code source} has no parent directory
     */
    public static Path destinationFor(Path source, BookFormat format, String targetLanguage) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(targetLanguage, "targetLanguage");
        final String fileName = Objects.requireNonNull(source.getFileName()).toString();
        final String suffix = format.matchedSuffix(fileName);
        final String outputName =
                fileName.substring(0, fileName.length() - suffix.length()) + "." + targetLanguage + suffix;
        final Path parent = source.getParent();
        return parent == null ? Path.of(outputName) : parent.resolve(outputName);
    }
}

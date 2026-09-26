package ua.bookloom.document.fb2;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jdom2.Element;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.MetadataKey;

/**
 * Reads the book's declared language, title and author out of {@code description/title-info}.
 *
 * <p>Absence is representable and never invented. Real books get this wrong constantly — one surveyed FB2
 * declares English on a French book and another declares no language at all — and the declared language exists
 * precisely so a later change can compare it against the language detected from the content. A guessed value
 * would make that comparison meaningless.
 *
 * <p>{@code src-lang} and {@code src-title-info} are deliberately not read here: they record what the book was
 * translated <em>from</em>, which is not this book's declared language.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class Fb2Metadata {

    private static final List<String> AUTHOR_NAME_PARTS = List.of("first-name", "middle-name", "last-name");

    /**
     * The language {@code title-info} declares.
     *
     * @param titleInfo the book's {@code title-info} element, or {@code null} when it has none
     * @return the declared language, or {@code null} when the source provides none
     */
    static @Nullable String declaredLang(@Nullable Element titleInfo) {
        return textOrNull(childOf(titleInfo, "lang"));
    }

    /**
     * The book-level metadata this change records — title and author.
     *
     * @param titleInfo the book's {@code title-info} element, or {@code null} when it has none
     * @return an ordered map carrying only the keys the source actually provides; never null, possibly empty
     */
    static Map<String, String> read(@Nullable Element titleInfo) {
        final Map<String, String> metadata = new LinkedHashMap<>();
        putIfPresent(metadata, MetadataKey.TITLE.key(), textOrNull(childOf(titleInfo, "book-title")));
        putIfPresent(metadata, MetadataKey.AUTHOR.key(), authorOf(titleInfo));
        return metadata;
    }

    /**
     * An author's display name, assembled from the name parts FB2 splits it across, falling back to the nickname
     * a book may carry instead of a real name.
     */
    private static @Nullable String authorOf(@Nullable Element titleInfo) {
        final Element author = childOf(titleInfo, "author");
        if (author == null) {
            return null;
        }
        final StringBuilder name = new StringBuilder();
        for (final String part : AUTHOR_NAME_PARTS) {
            appendPart(name, textOrNull(childOf(author, part)));
        }
        return name.isEmpty() ? textOrNull(childOf(author, "nickname")) : name.toString();
    }

    private static void appendPart(StringBuilder name, @Nullable String part) {
        if (part == null) {
            return;
        }
        if (!name.isEmpty()) {
            name.append(' ');
        }
        name.append(part);
    }

    /**
     * Finds a child by local name, ignoring namespace. Real books declare the FB2 namespace inconsistently — some
     * omit it entirely — and matching by local name reads all of them rather than only the well-formed ones.
     */
    static @Nullable Element childOf(@Nullable Element parent, String localName) {
        if (parent == null) {
            return null;
        }
        for (final Element child : parent.getChildren()) {
            if (localName.equals(child.getName())) {
                return child;
            }
        }
        return null;
    }

    private static @Nullable String textOrNull(@Nullable Element element) {
        if (element == null) {
            return null;
        }
        final String text = element.getTextNormalize();
        return text.isEmpty() ? null : text;
    }

    private static void putIfPresent(Map<String, String> metadata, String key, @Nullable String value) {
        if (value != null) {
            metadata.put(key, value);
        }
    }
}

package ua.bookloom.document.fixture;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Markdown fixtures, written as real files into a {@code @TempDir}.
 *
 * <p>Every element is here because it is something a renderer or re-encoder would quietly normalise: emphasis
 * spelling, bullet characters, fence style and info string, table alignment, a reference-link definition, a hard
 * line break, and the absence of a trailing newline.
 *
 * <p><strong>The hard line break cannot be written naively.</strong> A Java text block strips trailing whitespace
 * — javac warns about it — so the two spaces that <em>are</em> the hard break would vanish from the fixture and
 * the test would pass while proving nothing. The {@code \\s} escape emits a space and pins the line end, which is
 * the same class of hazard design.md D8 gives for committed byte-sequence fixtures, in a different guise.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MarkdownFixtures {

    /**
     * The primary Markdown fixture. Deliberately does <strong>not</strong> end with a newline: six of eight
     * surveyed corpus files do not, and an AST re-render would add one.
     */
    public static final String PRIMARY = """
            ---
            title: The Book
            lang: en
            ---

            # Chapter One

            Some _emphasis_ here and a [ref][r].

            ```java
            int x = 1;
            ---
            # not a heading
            ```

            | Left | Center | Right |
            |:-----|:------:|------:|
            | a1   | b1     | c1    |

            - item one

              ```bash
              echo hi
              ```

              more prose

            - item two
              - nested item

            > Quoted prose.

            <div class="note">Careful.</div>

            [r]: https://example.com

                indented code block

            A paragraph ending in a hard break \s
            and continuing after it.

            A paragraph whose own last line ends in trailing spaces. \s

            ---

            Last paragraph with no trailing newline.""";

    /** A file whose only content is one fenced block — the zero-segment case that must still round-trip. */
    public static final String ONLY_A_FENCE = """
            ```java
            int x = 1;
            ```
            """;

    /** A file with no frontmatter at all, so an export can be checked for not inventing one. */
    public static final String NO_FRONTMATTER = """
            # Title

            Prose one.

            ---

            Prose two.
            """;

    /**
     * A heading and three paragraphs where almost every word sits inside inline markup — {@code **bold**},
     * {@code _italic_}, {@code `code`} spans and links. This is the fixture that proves the coverage metric strips
     * markup symmetrically (design.md D5): measured against the raw, unstripped segment text a word like
     * {@code **Every**} never matches the denominator's clean {@code Every}, fabricating a shortfall on exactly
     * this shape.
     */
    public static final String MARKUP_DENSE = """
            # **Heavily Marked Chapter**

            This paragraph is **bold**, _italic_, `coded`, and linked via [a reference](https://example.com/page) \
            all within one short sentence full of markup.

            **Every** word here is wrapped: **one**, _two_, `three`, **_four_**, [five](https://example.com/five), \
            `six`, **seven**, _eight_.

            A third paragraph mixes `inline code`, **strong emphasis**, and _regular emphasis_ around a \
            [final link](https://example.com/final) to close things out.
            """;

    /**
     * Writes {@link #PRIMARY} as UTF-8.
     *
     * @param destination the file to write
     * @return {@code destination}
     */
    public static Path primary(Path destination) {
        return write(destination, PRIMARY);
    }

    /**
     * Writes {@link #ONLY_A_FENCE} as UTF-8.
     *
     * @param destination the file to write
     * @return {@code destination}
     */
    public static Path onlyAFence(Path destination) {
        return write(destination, ONLY_A_FENCE);
    }

    /**
     * Writes {@link #MARKUP_DENSE} as UTF-8.
     *
     * @param destination the file to write
     * @return {@code destination}
     */
    public static Path markupDense(Path destination) {
        return write(destination, MARKUP_DENSE);
    }

    /**
     * Writes {@code markdown} as UTF-8.
     *
     * @param destination the file to write
     * @param markdown the document text
     * @return {@code destination}
     */
    public static Path write(Path destination, String markdown) {
        try {
            Files.write(destination, markdown.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return destination;
    }
}

package ua.bookloom.document.fixture;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Plain-text fixtures.
 *
 * <p>These are the clearest case for design.md D8's rule that a fixture is built, not committed: this one
 * <em>is</em> a byte sequence — a byte-order mark, CRLF endings, four-space indentation and trailing whitespace —
 * and a committed file of that kind is one editor save or one {@code core.autocrlf} setting away from being
 * normalised into a fixture that no longer tests what it was written to test. An explicit
 * {@code "﻿…\r\n"} cannot be normalised by anything.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TxtFixtures {

    /**
     * A byte-order mark, CRLF endings throughout, a run of three blank lines, an indented paragraph and trailing
     * whitespace. Real files contain runs of several blank lines constantly — one surveyed file has 762.
     */
    public static final String PRIMARY = "﻿One.\r\n\r\n\r\n\r\n    Two, indented.   \r\n\r\nThree.\r\n";

    /**
     * Three Cyrillic paragraphs with no byte-order mark, so a real {@code windows-1251} file is proven against
     * {@link ua.bookloom.document.golden.FixtureSweepTest}'s coverage assertion decoded in its own charset rather
     * than the hardcoded UTF-8 the coverage metric used to apply regardless of the resolved encoding — a defect
     * that fabricated a ~10% shortfall on a real book carrying 22,298 replacement characters in the denominator.
     */
    public static final String WINDOWS_1251 = """
            Перший абзац книги з кількома словами.

            Другий абзац з іншими словами та Кирилицею.

            Третій, останній абзац цього файлу.
            """;

    /**
     * Writes {@link #PRIMARY} as UTF-8.
     *
     * @param destination the file to write
     * @return {@code destination}
     */
    public static Path primary(Path destination) {
        return write(destination, PRIMARY, StandardCharsets.UTF_8);
    }

    /**
     * Writes {@link #WINDOWS_1251} in the {@code windows-1251} encoding.
     *
     * @param destination the file to write
     * @return {@code destination}
     */
    public static Path windows1251(Path destination) {
        return write(destination, WINDOWS_1251, Charset.forName("windows-1251"));
    }

    /**
     * Writes {@code text} in {@code charset}.
     *
     * @param destination the file to write
     * @param text the document text
     * @param charset the encoding to write it in
     * @return {@code destination}
     */
    public static Path write(Path destination, String text, Charset charset) {
        try {
            Files.write(destination, text.getBytes(charset));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return destination;
    }
}

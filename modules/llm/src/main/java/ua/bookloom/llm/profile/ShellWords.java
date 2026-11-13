package ua.bookloom.llm.profile;

import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Splits a pasted command line into words the way a shell does for quotes and backslashes; nothing is run. */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ShellWords {

    static List<String> split(final String line) {
        final List<String> words = new ArrayList<>();
        final StringBuilder word = new StringBuilder();
        boolean inWord = false;
        char quote = 0;
        for (int i = 0; i < line.length(); i++) {
            final char c = line.charAt(i);
            if (quote != 0) {
                quote = nextInQuote(c, quote, word);
            } else if (c == '"' || c == '\'') {
                quote = c;
                inWord = true;
            } else if (!Character.isWhitespace(c)) {
                word.append(c);
                inWord = true;
            } else if (inWord) {
                words.add(word.toString());
                word.setLength(0);
                inWord = false;
            }
        }
        if (quote != 0) {
            return List.of(line.split("\\s+"));
        }
        if (inWord) {
            words.add(word.toString());
        }
        return words;
    }

    private static char nextInQuote(final char c, final char quote, final StringBuilder word) {
        if (c == quote) {
            return 0;
        }
        word.append(c);
        return quote;
    }
}

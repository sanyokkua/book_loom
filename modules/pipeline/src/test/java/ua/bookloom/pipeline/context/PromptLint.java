package ua.bookloom.pipeline.context;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.pipeline.WholeWord;
import ua.bookloom.pipeline.prompt.PromptHygiene;

/**
 * The prompt-hygiene lint: what must never reach a model besides its instructions. It reads the finished prompt text,
 * so it checks what is really sent, not what a builder meant to send.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class PromptLint {

    /** What the chunk being translated makes relevant. */
    record Facts(String chunkText, List<String> sameUnitTexts, List<String> otherUnitTexts) {}

    private static final Pattern BRACKET_HEADING = Pattern.compile("^\\[[^\\]]+]$");
    private static final Pattern COLON_HEADING = Pattern.compile("^[A-Z][^.]{0,90}:$");
    private static final Pattern PARAGRAPH_BREAK = Pattern.compile("\n\n");
    /** A batch prompt writes a locked-name line as {@code 3: ⟦g0⟧ → name}, because tokens are numbered per item. */
    private static final Pattern ITEM_PREFIX = Pattern.compile("^[A-Za-z0-9_.:-]+: (?=⟦g)");

    private static final int MIN_DUPLICATE_LENGTH = 15;
    private static final Set<String> GLOSSARY_HEADINGS = Set.of("[Glossary", "[Locked names", "[Suggested renderings");

    static List<String> violations(final String prompt, final Facts facts) {
        final List<String> found = new ArrayList<>();
        final List<String> lines = prompt.lines().toList();
        emptyHeadings(lines, found);
        duplicates(lines, found);
        filler(lines, found);
        glossaryTerms(lines, facts, found);
        previousPairs(lines, facts, found);
        return found;
    }

    private static boolean isHeading(final String line) {
        final String text = line.strip();
        return BRACKET_HEADING.matcher(text).matches()
                || COLON_HEADING.matcher(text).matches();
    }

    private static void emptyHeadings(final List<String> lines, final List<String> found) {
        for (int i = 0; i < lines.size(); i++) {
            if (isHeading(lines.get(i))
                    && nextContent(lines, i).map(PromptLint::isHeading).orElse(true)) {
                found.add("empty heading: " + lines.get(i));
            }
        }
    }

    private static Optional<String> nextContent(final List<String> lines, final int from) {
        return lines.stream().skip(from + 1L).filter(line -> !line.isBlank()).findFirst();
    }

    private static void duplicates(final List<String> lines, final List<String> found) {
        final Set<String> seen = new HashSet<>();
        for (final String line : lines) {
            final String text = line.strip();
            if (text.length() >= MIN_DUPLICATE_LENGTH && !seen.add(text)) {
                found.add("duplicate line: " + text);
            }
        }
    }

    private static void filler(final List<String> lines, final List<String> found) {
        for (final String line : lines) {
            if (!line.isBlank() && PromptHygiene.isFiller(line)) {
                found.add("filler line: " + line);
            }
        }
    }

    private static void glossaryTerms(final List<String> lines, final Facts facts, final List<String> found) {
        boolean inGlossary = false;
        for (final String line : lines) {
            if (line.isBlank()) {
                inGlossary = false;
            } else if (isHeading(line)) {
                inGlossary = GLOSSARY_HEADINGS.stream().anyMatch(line::startsWith);
            } else if (inGlossary && line.contains(" → ") && !termPresent(line, facts.chunkText())) {
                found.add("glossary term not in the chunk: " + line);
            }
        }
    }

    private static boolean termPresent(final String glossaryLine, final String chunkText) {
        final String term = ITEM_PREFIX
                .matcher(glossaryLine.substring(0, glossaryLine.indexOf(" → ")))
                .replaceFirst("");
        return term.startsWith("⟦g")
                ? chunkText.contains(term)
                : WholeWord.pattern(term).matcher(chunkText).find();
    }

    private static void previousPairs(final List<String> lines, final Facts facts, final List<String> found) {
        final String all = String.join("\n", lines);
        final int open = all.indexOf("<PreviousTranslations>");
        final int close = all.indexOf("</PreviousTranslations>");
        if (open < 0 || close < open) {
            return;
        }
        final String block = all.substring(open + "<PreviousTranslations>".length(), close);
        for (final String paragraph :
                PARAGRAPH_BREAK.splitAsStream(block.strip()).toList()) {
            if (facts.otherUnitTexts().contains(paragraph)
                    || !facts.sameUnitTexts().contains(paragraph)) {
                found.add("previous text not from this chapter: " + paragraph);
            }
        }
    }
}

package ua.bookloom.document.mask;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.Segment;

/**
 * Substitutes every {@code ⟦gN⟧} token in a translated target with the exact source fragment it replaced — the
 * requirement <em>Restore a translated segment by substituting each placeholder back</em>.
 *
 * <p><strong>Callers must run {@link PlaceholderGate#compare} first.</strong> This class does not — restoring is
 * the second of the two steps the frozen rule separates, and by the time it runs, every token in {@code target} is
 * already known to have a mapped fragment (the gate having passed means {@code target}'s token multiset equals
 * {@code segment.masked()}'s, and the mask-time bijection invariant guarantees every token in {@code masked} has an
 * entry in {@code segment.placeholders()}).
 *
 * <p>Substitution is one {@link Matcher} pass over {@code target}, matching the whole token grammar — never a loop
 * over the placeholder map's keys calling {@code String.replace} for each. That obvious-looking alternative has two
 * bugs the frozen spec warns about: {@code ⟦g1⟧} mis-matching as a prefix of {@code ⟦g12⟧}, and a token spelled
 * inside an already-substituted fragment (a code span whose own text is {@code ⟦g0⟧}) being expanded a second time.
 * A single forward pass that only ever advances past what it has already consumed cannot do either.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Unmasker {

    /**
     * Restores {@code target} against {@code segment}'s placeholder map.
     *
     * @param format the segment's book format, which selects the escaping rule for the text between tokens — EPUB
     *     and FB2 escape {@code &}, {@code <} and {@code >} as character data; Markdown and TXT leave it as written
     * @param segment the segment whose placeholder map supplies each token's mapped fragment; never null
     * @param target the translated text to restore, already validated by {@link PlaceholderGate#compare}; never
     *     null
     * @return the restored content and where each mapped fragment landed in it; never null
     */
    public static RestoredContent restore(BookFormat format, Segment segment, String target) {
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(segment, "segment");
        Objects.requireNonNull(target, "target");
        final UnaryOperator<String> escape = escaperFor(format);
        final StringBuilder restored = new StringBuilder();
        final List<FragmentRange> fragmentRanges = new ArrayList<>();
        final Matcher matcher = Placeholders.matcher(target);
        int last = 0;
        while (matcher.find()) {
            restored.append(escape.apply(target.substring(last, matcher.start())));
            // Not a re-check of what the gate just proved: the gate compares the target's tokens against the
            // segment's OWN masked form, so it passes for a segment whose masked form and placeholder map
            // disagree — a state the mask-time invariant rules out for a parsed segment but that a hand-built
            // one reaches. Without this, the map's miss appends the four characters "null" into a book.
            final String fragment = Objects.requireNonNull(
                    segment.placeholders().get(Placeholders.keyOf(matcher.group())),
                    "no fragment mapped for " + matcher.group());
            final int fragmentStart = restored.length();
            restored.append(fragment);
            fragmentRanges.add(new FragmentRange(fragmentStart, restored.length()));
            last = matcher.end();
        }
        restored.append(escape.apply(target.substring(last)));
        return new RestoredContent(restored.toString(), fragmentRanges);
    }

    private static UnaryOperator<String> escaperFor(BookFormat format) {
        return switch (format) {
            case EPUB, FB2 -> MarkupEscape::escapeCharacterData;
            case MARKDOWN, TXT -> UnaryOperator.identity();
        };
    }
}

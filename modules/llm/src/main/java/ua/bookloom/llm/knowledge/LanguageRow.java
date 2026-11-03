package ua.bookloom.llm.knowledge;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import ua.bookloom.api.llm.LanguageDeclaration;
import ua.bookloom.api.llm.ListKind;

/**
 * One row of {@code declared-languages.csv}.
 *
 * @param key the unique name of the row
 * @param matchKind how {@code pattern} is compared with a model name
 * @param pattern lower-case name segments joined by "-"
 * @param listKind how far the list can be trusted
 * @param codes the lower-case primary tags the list names; empty for a row without a list
 * @param source a short text naming where the list comes from
 * @param evidence free text; may carry the token {@code BOOKLOOM-RUN:ru>uk} for a pair BookLoom's own run proved
 */
public record LanguageRow(
        String key,
        MatchKind matchKind,
        String pattern,
        ListKind listKind,
        Set<String> codes,
        String source,
        String evidence) {

    public LanguageRow {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(matchKind, "matchKind");
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(listKind, "listKind");
        Objects.requireNonNull(codes, "codes");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(evidence, "evidence");
        codes = Set.copyOf(codes);
    }

    /**
     * The row as the contract record the rest of the program reads.
     *
     * @return the declaration; its codes are this row's codes
     */
    public LanguageDeclaration declaration() {
        return new LanguageDeclaration(listKind, codes, source);
    }

    /**
     * The pattern split into its whole segments.
     *
     * @return the segments in order; never empty
     */
    public List<String> segments() {
        return List.of(pattern.split("[-:]"));
    }
}

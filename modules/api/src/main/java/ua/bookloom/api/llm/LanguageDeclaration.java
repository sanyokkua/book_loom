package ua.bookloom.api.llm;

import java.util.Objects;
import java.util.Set;

/**
 * What a model's vendor declares about languages.
 *
 * @param listKind how far the list can be trusted
 * @param codes the lower-case primary language tags the list names; empty for {@link ListKind#NONE}
 * @param source a short text naming where the list comes from
 */
public record LanguageDeclaration(ListKind listKind, Set<String> codes, String source) {

    public LanguageDeclaration {
        Objects.requireNonNull(listKind, "listKind");
        Objects.requireNonNull(codes, "codes");
        Objects.requireNonNull(source, "source");
        codes = Set.copyOf(codes);
    }
}

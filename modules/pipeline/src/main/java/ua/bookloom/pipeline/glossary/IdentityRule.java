package ua.bookloom.pipeline.glossary;

import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.util.lang.Languages;
import ua.bookloom.util.lang.Script;

/**
 * The one rule about a rendering that is the source spelling (15i.N6): across scripts it is no translation and is
 * refused, unless the entry is a brand, an identifier or kept as written. In one script (English to German) a copy can
 * be right. Shared by the name suggestion, the term-mapping check and the recurring-term path.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class IdentityRule {

    /** Why a source spelling may stay as the rendering. */
    public enum Kept {
        /** Nothing: the rendering must differ from the source across scripts. */
        NOT_KEPT,
        /** The reading says the entry is a brand or a product. */
        BRAND,
        /** The person's name policy or the entry keeps the original. */
        KEEP_ORIGINAL
    }

    /**
     * Whether a rendering is a refused copy of the source.
     *
     * @param term the source term; never null
     * @param rendering the suggested rendering; never null
     * @param sourceTag the source language tag; null when the book's language is not known, which allows the copy
     * @param targetTag the target language tag; null when not known, which allows the copy
     * @param kept why the source may stay; never null
     * @return {@code true} if the rendering equals the term (ignoring case and outer space), the languages are
     *     written in different scripts, the term has a letter, and it is no brand, identifier or kept original;
     *     {@code false} otherwise, including when a script is not known
     */
    public static boolean refuses(
            final String term,
            final String rendering,
            @Nullable final String sourceTag,
            @Nullable final String targetTag,
            final Kept kept) {
        Objects.requireNonNull(term, "term");
        Objects.requireNonNull(rendering, "rendering");
        Objects.requireNonNull(kept, "kept");
        log.debug("Identity rule {}->{} kept={}", sourceTag, targetTag, kept);
        log.trace("Identity rule term='{}' rendering='{}'", term, rendering);
        if (!term.strip().equalsIgnoreCase(rendering.strip()) || kept != Kept.NOT_KEPT || isIdentifier(term)) {
            return false;
        }
        final boolean refused = term.codePoints().anyMatch(Character::isLetter) && acrossScripts(sourceTag, targetTag);
        log.debug("Identity rule: a copy of the source is {}", refused ? "refused" : "allowed");
        return refused;
    }

    private static boolean acrossScripts(@Nullable final String sourceTag, @Nullable final String targetTag) {
        final Optional<Script> source = Languages.scriptOf(sourceTag);
        final Optional<Script> target = Languages.scriptOf(targetTag);
        return source.isPresent() && target.isPresent() && source.get() != target.get();
    }

    /**
     * Whether a term is an identifier: one token, no space, holding both a letter and a digit.
     *
     * @param term the source term; never null
     * @return {@code true} for {@code ZX-81}, {@code A7} or a UUID; {@code false} for a plain word, a bare number or
     *     a phrase
     */
    public static boolean isIdentifier(final String term) {
        Objects.requireNonNull(term, "term");
        final String stripped = term.strip();
        return stripped.codePoints().noneMatch(Character::isWhitespace)
                && stripped.codePoints().anyMatch(Character::isLetter)
                && stripped.codePoints().anyMatch(Character::isDigit);
    }
}

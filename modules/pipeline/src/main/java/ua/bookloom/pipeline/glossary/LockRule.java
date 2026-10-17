package ua.bookloom.pipeline.glossary;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * When a suggested rendering is locked (15i.N6, 15j.J1). An identifier or a kept original (a name, or a brand, under
 * Keep original) is locked when it is kept verbatim. A verified proper name with a checked rendering that is not a
 * speaking name is locked only when the target language does not decline names; where names inflect the rendering
 * stays guidance held by the name checks. A brand follows the name policy like any name: it has no lock of its own.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LockRule {

    /**
     * What is known of the entry.
     *
     * @param keepOriginal whether the name policy keeps the original spelling of this entry (a name or a brand)
     * @param verifiedName whether the readings verified it as a proper name; never for a common noun
     * @param speaking whether the reading says its meaning matters, so it is translated
     * @param renderingChecked whether the rendering passed the letter table and the identity rule
     */
    public record Basis(boolean keepOriginal, boolean verifiedName, boolean speaking, boolean renderingChecked) {}

    /**
     * Decides whether to lock.
     *
     * @param term the source term; never null
     * @param rendering the chosen rendering; never null
     * @param basis what is known of the entry; never null
     * @param namesInflect whether the target language declines names (see {@code LanguageRules.namesInflect})
     * @return {@code true} to lock the entry with this rendering, {@code false} to leave it unlocked
     */
    public static boolean locks(
            final String term, final String rendering, final Basis basis, final boolean namesInflect) {
        Objects.requireNonNull(term, "term");
        Objects.requireNonNull(rendering, "rendering");
        Objects.requireNonNull(basis, "basis");
        final boolean isKept = (basis.keepOriginal() || IdentityRule.isIdentifier(term))
                && term.strip().equals(rendering.strip());
        final boolean isNameLock =
                basis.verifiedName() && !basis.speaking() && basis.renderingChecked() && !namesInflect;
        log.debug(
                "Lock rule {} namesInflect={}: kept verbatim {}, name lock {}",
                basis,
                namesInflect,
                isKept,
                isNameLock);
        log.trace("Lock rule term='{}' rendering='{}'", term, rendering);
        return isKept || isNameLock;
    }
}

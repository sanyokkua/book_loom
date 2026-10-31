package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.pipeline.glossary.LockRule.Basis;
import ua.bookloom.pipeline.glossary.LockRule.Decision;

/**
 * Which suggestions are locked (15i.N6, 15j.J1): an identifier or a kept original (a name, or a brand, under Keep
 * original) kept verbatim; or a verified proper name with a checked rendering that is not a speaking name, in a target
 * whose names do not inflect. Anywhere names inflect, the rendering stays unlocked guidance held by the name checks.
 */
class LockRuleTest {

    @ParameterizedTest(name = "{0} -> {1} [keep={2} name={3} speaking={4} checked={5} inflects={6}]: {7}")
    @CsvSource(
            delimiter = '|',
            value = {
                // term | rendering | keepOriginal | verifiedName | speaking | checked | namesInflect | locks
                // kept verbatim: locked whether or not the target inflects
                "KESSEL OPTIK|KESSEL OPTIK|true|false|false|false|true|true",
                "KESSEL OPTIK|KESSEL OPTIK|true|false|false|false|false|true",
                "ZX-81|ZX-81|false|false|false|false|true|true",
                "Arlo|Arlo|true|false|false|false|true|true",
                // the same entries rendered, not kept verbatim: no lock on that ground
                "KESSEL OPTIK|Кессел Оптік|true|false|false|true|true|false",
                "ZX-81|ZX-82|false|false|false|false|true|false",
                "Arlo|Арло|true|false|false|false|true|false",
                // an ordinary name or a brand copied with no ground: the policy does not keep it
                "Arlo|Arlo|false|false|false|false|true|false",
                "KESSEL OPTIK|KESSEL OPTIK|false|false|false|true|true|false",
                // a verified, checked, not speaking name: locked only where names do not inflect
                "Arlo|Arlo|false|true|false|true|false|true",
                "Venn|Venn|false|true|false|true|false|true",
                "Arlo|Арло|false|true|false|true|true|false",
                // each missing condition keeps it unlocked even where names do not inflect
                "Arlo|Arlo|false|true|true|true|false|false",
                "Arlo|Arlo|false|false|false|true|false|false",
                "Arlo|Arlo|false|true|false|false|false|false",
            })
    void locks_byBasisAndTarget_followsTheRule(
            final String term,
            final String rendering,
            final boolean keepOriginal,
            final boolean verifiedName,
            final boolean speaking,
            final boolean checked,
            final boolean namesInflect,
            final boolean expected) {
        final Basis basis = new Basis(keepOriginal, verifiedName, speaking, checked);

        assertThat(LockRule.locks(term, rendering, basis, namesInflect)).isEqualTo(expected);
    }

    // IF an accepted name were locked where names inflect, THEN a placeholder would hide it and its endings be lost.
    @Test
    void accept_inflectingTarget_isPinned() {
        final Basis verified = new Basis(false, true, false, true);

        assertThat(LockRule.decide("Hale", "Гейл", verified, true)).isEqualTo(Decision.PINNED);
        assertThat(LockRule.decide("Venn", "Venn", verified, false)).isEqualTo(Decision.LOCKED);
    }

    @Test
    void accept_identifier_isLocked() {
        final Basis nothingKnown = new Basis(false, false, false, false);

        assertThat(LockRule.decide("ZX-81", "ZX-81", nothingKnown, true)).isEqualTo(Decision.LOCKED);
    }
}

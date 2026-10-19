package ua.bookloom.api.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.document.SegmentKind;

/**
 * {@code AlsoTranslate}'s per-kind coverage and kept-kinds derivation.
 */
class AlsoTranslateTest {

    @ParameterizedTest
    @CsvSource({
        "ALT, true, false, true, false, false",
        "FRONTMATTER_VALUE, true, true, true, false, false",
        "METADATA_AUTHOR, true, true, true, false, true",
        "METADATA_DESCRIPTION, true, true, false, false, false",
        "TITLE, false, true, true, false, false",
        "TITLE, true, true, true, false, true",
        "PARAGRAPH, false, false, false, false, true"
    })
    void covers_kindAndSwitches_matchesExpected(
            final SegmentKind kind,
            final boolean navigationLabels,
            final boolean altText,
            final boolean metadata,
            final boolean frontmatter,
            final boolean expected) {
        final AlsoTranslate alsoTranslate = new AlsoTranslate(navigationLabels, altText, metadata, frontmatter);

        assertThat(alsoTranslate.covers(kind)).isEqualTo(expected);
    }

    @Test
    void keptKinds_defaults_isExactlyFrontmatterValue() {
        assertThat(AlsoTranslate.defaults().keptKinds()).containsExactlyInAnyOrder(SegmentKind.FRONTMATTER_VALUE);
    }

    @Test
    void keptKinds_everySwitchOff_isEveryAuxiliaryKind() {
        final AlsoTranslate alsoTranslate = new AlsoTranslate(false, false, false, false);

        assertThat(alsoTranslate.keptKinds())
                .isEqualTo(Set.of(
                        SegmentKind.NAV_LABEL,
                        SegmentKind.TITLE,
                        SegmentKind.ALT,
                        SegmentKind.METADATA_TITLE,
                        SegmentKind.METADATA_AUTHOR,
                        SegmentKind.METADATA_DESCRIPTION,
                        SegmentKind.FRONTMATTER_VALUE));
    }
}

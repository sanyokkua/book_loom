package ua.bookloom.persistence.sqlite;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StoredShapesTest {

    private enum Colour {
        RED,
        BLUE
    }

    private record Inner(Colour colour, int weight) {}

    private record Small(String name, List<Inner> parts, Map<String, Integer> counts) {}

    private record SmallRenamed(String name, List<Inner> parts, Map<String, Integer> counts) {}

    private record SmallGrown(String name, List<Inner> parts, Map<String, Integer> counts, boolean extra) {}

    private record InnerTyped(Colour colour, long weight) {}

    private record SmallRetyped(String name, List<InnerTyped> parts, Map<String, Integer> counts) {}

    @Test
    void describe_record_listsComponentsByNameWithNestedRecordsAndEnumsInline() {
        assertThat(StoredShapes.describe(List.of(Small.class)))
                .isEqualTo("{counts:java.util.Map<java.lang.String,java.lang.Integer>,name:java.lang.String,"
                        + "parts:java.util.List<{colour:enum[BLUE,RED],weight:int}>}");
    }

    @Test
    void fingerprint_tinyRecord_isThePinnedValue() {
        assertThat(StoredShapes.fingerprint(List.of(Small.class)))
                .isEqualTo("756bd83380ce887aff7e96fe169b61342aaa71e3ef0929cacfad16da8942754b");
    }

    @Test
    void fingerprint_sameShapeUnderAnotherName_isEqual() {
        assertThat(StoredShapes.fingerprint(List.of(SmallRenamed.class)))
                .isEqualTo(StoredShapes.fingerprint(List.of(Small.class)));
    }

    @Test
    void fingerprint_componentAdded_differs() {
        assertThat(StoredShapes.fingerprint(List.of(SmallGrown.class)))
                .isNotEqualTo(StoredShapes.fingerprint(List.of(Small.class)));
    }

    @Test
    void fingerprint_nestedComponentRetyped_differs() {
        assertThat(StoredShapes.fingerprint(List.of(SmallRetyped.class)))
                .isNotEqualTo(StoredShapes.fingerprint(List.of(Small.class)));
    }

    @Test
    void fingerprint_afterEngineFields_differsFromTheStoredOne() {
        final String described = StoredShapes.describe(StoredShapes.ROOTS);
        final String withoutNewFields = described.replace("engineChoice:", "").replace("probeVerdicts:", "");

        assertThat(described).contains("engineChoice:", "engineVersion:", "nativeOfferDeclined:", "probeVerdicts:");
        assertThat(withoutNewFields).isNotEqualTo(described);
    }

    @Test
    void fingerprint_afterModelOverrides_differsFromTheStoredOne() {
        final String described = StoredShapes.describe(StoredShapes.ROOTS);
        final String withoutNewFields = described
                .replace("samplingOverride:", "")
                .replace("thinkingOverride:", "")
                .replace(",scope:java.lang.String", "");

        assertThat(described)
                .contains("thinkingOverride:enum[ALLOW_PROOFREAD_AND_NAMES,ALWAYS,RECOMMENDED]")
                .contains("samplingOverride:{minP:java.lang.Double,mode:enum[CUSTOM,PROFILE_DEFAULT]")
                .contains("scope:java.lang.String");
        assertThat(withoutNewFields).isNotEqualTo(described);
    }

    // Proves the stored glossary and lexicon shapes carry the lemma fields, so a database from before them is reset.
    @Test
    void fingerprint_afterLemmaEntries_differsFromTheStoredOne() {
        final String described = StoredShapes.describe(StoredShapes.ROOTS);
        final String withoutNewFields = described
                .replace("forms:", "")
                .replace("lemmaInferred:", "")
                .replace("targetState:", "")
                .replace("placeKind:", "");

        assertThat(described)
                .contains("forms:java.util.List<java.lang.String>")
                .contains("lemmaInferred:boolean")
                .contains("targetState:enum[LOCKED,PINNED,SUGGESTED]")
                .contains("placeKind:enum[CITY,COUNTRY,REGION,RIVER]")
                .contains("genderOrigin:enum[BOOK,LIKELY,RULE,USER]");
        assertThat(withoutNewFields).isNotEqualTo(described);
    }

    @Test
    void current_twoCalls_answerTheSameFingerprint() {
        assertThat(StoredShapes.current()).isEqualTo(StoredShapes.current()).hasSize(64);
    }

    // The new Brief keys and the per-kind speeds are part of the stored shapes, so an older database is set aside.
    @Test
    void fingerprint_afterBriefKeysAndSecondsPerCall_differs() {
        final String described = StoredShapes.describe(StoredShapes.ROOTS);

        assertThat(described).contains("secondsPerCall:", "quoteStyle:", "defaultAddress:", "apostrophe:");
        assertThat(described).doesNotContain("measuredSecondsPerSegment");
    }

    // A segment record no longer counts repair rounds (its components are listed in name order, so the reviewed mark
    // now follows the rejected reply directly), and a database that stored the count is set aside.
    @Test
    void describe_afterRepairRoundsGone_holdsNoRepairRounds() {
        assertThat(StoredShapes.describe(StoredShapes.ROOTS))
                .contains("rejectedTarget:java.lang.String,reviewed:boolean")
                .doesNotContain("repairRounds:");
    }
}

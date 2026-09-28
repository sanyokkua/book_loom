package ua.bookloom.api.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.document.Unit;

/**
 * {@code SegmentRecord}'s effective target, defensive copy, edit round trip and kept-as-source rule.
 */
class SegmentRecordTest {

    private static SegmentRecord recordOf(
            final String unitId,
            final SegmentKind kind,
            final SegmentStatus status,
            @Nullable final String machineTarget,
            @Nullable final String userTarget) {
        return new SegmentRecord(
                "p1",
                unitId + ":0",
                unitId,
                0,
                kind,
                status,
                machineTarget,
                machineTarget,
                userTarget,
                userTarget,
                0.0,
                null,
                List.of(),
                SegmentPath.DRAFT,
                0,
                false,
                null);
    }

    @Test
    void effectiveTarget_machineAndUser_returnsUser() {
        final SegmentRecord record = recordOf("ch1", SegmentKind.PARAGRAPH, SegmentStatus.ACCEPTED, "Хата", "Дім");

        assertThat(record.effectiveTarget()).isPresent().hasValue("Дім");
    }

    @Test
    void effectiveTarget_machineOnly_returnsMachine() {
        final SegmentRecord record = recordOf("ch1", SegmentKind.PARAGRAPH, SegmentStatus.ACCEPTED, "Хата", null);

        assertThat(record.effectiveTarget()).isPresent().hasValue("Хата");
    }

    @Test
    void effectiveTarget_neither_isEmpty() {
        final SegmentRecord record = recordOf("ch1", SegmentKind.PARAGRAPH, SegmentStatus.PENDING, null, null);

        assertThat(record.effectiveTarget()).isEmpty();
    }

    @Test
    void constructor_findingsListMutatedAfterConstruction_doesNotChangeRecord() {
        final List<QaFinding> findings = new ArrayList<>();
        findings.add(new QaFinding("tag-mismatch", Severity.HIGH, "note", "placeholder"));
        final SegmentRecord record = new SegmentRecord(
                "p1",
                "ch1:0",
                "ch1",
                0,
                SegmentKind.PARAGRAPH,
                SegmentStatus.FLAGGED,
                null,
                null,
                null,
                null,
                0.0,
                null,
                findings,
                SegmentPath.DRAFT,
                0,
                false,
                null);

        findings.add(new QaFinding("other", Severity.LOW, "note2", "judge"));

        assertThat(record.findings()).hasSize(1);
    }

    @Test
    void withUserTarget_plainAndMasked_keepsBothForms() {
        final SegmentRecord record = recordOf("ch1", SegmentKind.PARAGRAPH, SegmentStatus.FLAGGED, "Хата", null);

        final SegmentRecord edited = record.withUserTarget("Дім", "⟦g0⟧Дім⟦g1⟧");

        assertThat(edited.userTarget()).isEqualTo("Дім");
        assertThat(edited.maskedUserTarget()).isEqualTo("⟦g0⟧Дім⟦g1⟧");
        assertThat(edited.machineTarget()).isEqualTo("Хата");
    }

    @Test
    void withUserTarget_nullBoth_clearsUserAndKeepsMachine() {
        final SegmentRecord record = recordOf("ch1", SegmentKind.PARAGRAPH, SegmentStatus.FLAGGED, "Хата", "Дім");

        final SegmentRecord cleared = record.withUserTarget(null, null);

        assertThat(cleared.userTarget()).isNull();
        assertThat(cleared.maskedUserTarget()).isNull();
        assertThat(cleared.machineTarget()).isEqualTo("Хата");
        assertThat(cleared.maskedMachineTarget()).isEqualTo("Хата");
    }

    @Test
    void constructor_newRecord_isNotReviewed() {
        final SegmentRecord record = recordOf("ch1", SegmentKind.PARAGRAPH, SegmentStatus.PENDING, null, null);

        assertThat(record.reviewed()).isFalse();
    }

    @Test
    void isKeptAsSource_auxiliaryKindInSet_isTrueEvenWhenFlagged() {
        final SegmentRecord record =
                recordOf(Unit.AUXILIARY_ID, SegmentKind.FRONTMATTER_VALUE, SegmentStatus.FLAGGED, null, null);

        assertThat(record.isKeptAsSource(Set.of(SegmentKind.FRONTMATTER_VALUE))).isTrue();
    }

    @Test
    void isKeptAsSource_bodyUnitKindInSet_isFalse() {
        final SegmentRecord record = recordOf("ch1.xhtml", SegmentKind.TITLE, SegmentStatus.PENDING, null, null);

        assertThat(record.isKeptAsSource(Set.of(SegmentKind.TITLE))).isFalse();
    }

    @Test
    void isKeptAsSource_auxiliaryKindNotInEmptySet_isFalse() {
        final SegmentRecord record =
                recordOf(Unit.AUXILIARY_ID, SegmentKind.METADATA_TITLE, SegmentStatus.PENDING, null, null);

        assertThat(record.isKeptAsSource(Set.of())).isFalse();
    }
}

package ua.bookloom.pipeline.typography;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.api.document.PlaceholderRepair;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.project.Severity;
import ua.bookloom.pipeline.heal.GateFunction;
import ua.bookloom.pipeline.heal.GateResult;

/** The gate wrapper hands the wrapped gate normalised text and adds one low note, and changes nothing else. */
class TypographyGateTest {

    private final List<String> seen = new ArrayList<>();
    private final GateFunction recording = (segment, reply) -> {
        seen.add(reply);
        return new GateResult.Restored(reply, reply);
    };

    private static Segment segment() {
        return new Segment(
                "u1:0",
                "u1",
                0,
                SegmentKind.PARAGRAPH,
                "x",
                "x",
                Map.of(),
                "hash",
                null,
                null,
                new ByteSpanAnchor(0, 1),
                null,
                SegmentStatus.PENDING,
                0.0,
                null,
                List.of(),
                List.of());
    }

    @Test
    void restore_textNeedingTypography_passesNormalisedTextAndAddsALowNote() {
        final GateResult result = TypographyGate.around(recording, "uk").restore(segment(), "Не пам'ятаю...");

        assertThat(seen).containsExactly("Не пам’ятаю…");
        assertThat(result).isInstanceOfSatisfying(GateResult.Restored.class, restored -> {
            assertThat(restored.maskedForm()).isEqualTo("Не пам’ятаю…");
            assertThat(restored.normalised()).isNotNull();
            assertThat(restored.normalised().severity()).isEqualTo(Severity.LOW);
            assertThat(restored.normalised().raisedBy()).isEqualTo("normalised");
            assertThat(restored.normalised().note()).isEqualTo("Typography normalised: 1 apostrophe, 1 ellipsis.");
        });
    }

    @Test
    void restore_textNeedingTypography_handsOnTheNormalisedCandidateWithItsTokens() {
        final GateResult result = TypographyGate.around(recording, "uk").restore(segment(), "Не пам'ятаю ⟦g0⟧...");

        assertThat(result)
                .isInstanceOfSatisfying(
                        GateResult.Restored.class,
                        restored -> assertThat(restored.maskedCandidate()).isEqualTo("Не пам’ятаю ⟦g0⟧…"));
    }

    @Test
    void restoreRepairing_cleanText_addsNoNote() {
        final GateResult result = TypographyGate.around(recording, "uk")
                .restoreRepairing(segment(), "Усе гаразд.", PlaceholderRepair.RESTORE_MISSING);

        assertThat(result).isInstanceOfSatisfying(GateResult.Restored.class, restored -> {
            assertThat(restored.normalised()).isNull();
            assertThat(restored.maskedForm()).isEqualTo("Усе гаразд.");
        });
    }
}

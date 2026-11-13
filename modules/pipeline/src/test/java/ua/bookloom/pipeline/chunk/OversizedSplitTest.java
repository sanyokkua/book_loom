package ua.bookloom.pipeline.chunk;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.api.document.PlaceholderPair;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.document.SentenceSplitter;
import ua.bookloom.api.llm.ModelProfile;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.pipeline.context.WindowBudgets;

/** An oversized segment is cut at sentence boundaries above the tier's current-part budget, never inside a pair. */
class OversizedSplitTest {

    private static final SentenceSplitter SPLITTER =
            Guice.createInjector(new DocumentModule()).getInstance(SentenceSplitter.class);
    private static final PlaceholderPair PAIR = new PlaceholderPair("⟦g3⟧", "⟦g4⟧", null);
    // 680 Latin characters are 191 tokens, so four runs are 764, over the small tier's 600.
    private static final String SENTENCE = "The road ran on. ".repeat(40);

    private static Segment segment(final String masked) {
        return new Segment(
                "ch02.xhtml:7",
                "ch02.xhtml",
                7,
                SegmentKind.PARAGRAPH,
                masked,
                masked,
                Map.of(),
                "hash",
                null,
                null,
                new ByteSpanAnchor(0, masked.length()),
                null,
                SegmentStatus.PENDING,
                0.0,
                null,
                List.of(PAIR),
                List.of());
    }

    @Test
    void plan_boundaryInsideAPair_keepsThePairInOnePiece() {
        final String masked = SENTENCE + SENTENCE + "⟦g3⟧" + SENTENCE + SENTENCE + "⟦g4⟧ The end.";

        final OversizedSplit.Plan plan = OversizedSplit.plan(
                segment(masked), masked, "en", WindowBudgets.currentPart(ModelProfile.Tier.SMALL), SPLITTER);

        assertThat(plan).isInstanceOfSatisfying(OversizedSplit.Pieces.class, pieces -> {
            assertThat(String.join("", pieces.pieces())).isEqualTo(masked);
            assertThat(pieces.pieces())
                    .filteredOn(piece -> piece.contains("⟦g3⟧"))
                    .singleElement()
                    .satisfies(piece -> assertThat(piece).contains("⟦g4⟧"));
        });
    }

    @Test
    void plan_everyBoundaryInsideAPair_isUnsplittable() {
        final String masked = "⟦g3⟧" + SENTENCE.repeat(5) + "⟦g4⟧";

        final OversizedSplit.Plan plan = OversizedSplit.plan(
                segment(masked), masked, "en", WindowBudgets.currentPart(ModelProfile.Tier.SMALL), SPLITTER);

        assertThat(plan).isInstanceOf(OversizedSplit.Unsplittable.class);
    }
}

package ua.bookloom.pipeline.chunk;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.document.SentenceSplitter;
import ua.bookloom.document.DocumentModule;

/** A long paragraph (15i.X3) is cut at sentence ends into groups of at most 90 words that join back to the whole. */
class OversizedSplitGroupsTest {

    private static final int GROUP_WORDS = 90;
    private static final SentenceSplitter SPLITTER =
            Guice.createInjector(new DocumentModule()).getInstance(SentenceSplitter.class);

    @Test
    void planByWords_publicDomainParagraphOf187Words_makesGroupsOfAtMost90WordsJoiningToTheWhole() throws IOException {
        final String text = paragraph("lp-01");

        final OversizedSplit.Plan plan = OversizedSplit.planByWords(segmentOf(text), text, "en", GROUP_WORDS, SPLITTER);

        final List<String> groups = ((OversizedSplit.Pieces) plan).pieces();
        assertThat(groups).hasSizeBetween(3, 5);
        assertThat(String.join("", groups)).isEqualTo(text);
        assertThat(groups)
                .allSatisfy(group -> assertThat(group.strip().split("\\s+")).hasSizeLessThanOrEqualTo(GROUP_WORDS));
        assertThat(groups).allSatisfy(group -> assertThat(group.strip()).isNotEmpty());
    }

    @Test
    void planByWords_everyParagraphOfTheSet_joinsBackToTheWholeWithinTheLimit() throws IOException {
        final List<String> texts = paragraphs();

        assertThat(texts).hasSize(20).allSatisfy(text -> {
            final OversizedSplit.Plan plan =
                    OversizedSplit.planByWords(segmentOf(text), text, "en", GROUP_WORDS, SPLITTER);
            final List<String> groups = ((OversizedSplit.Pieces) plan).pieces();
            assertThat(String.join("", groups)).isEqualTo(text);
            assertThat(groups)
                    .allSatisfy(group -> assertThat(LongParagraphs.words(group)).isLessThanOrEqualTo(GROUP_WORDS));
        });
    }

    // A sentence longer than the limit cannot be cut, so it is a group of its own and its neighbours are not merged
    // into it.
    @Test
    void planByWords_sentenceAboveTheLimit_isItsOwnGroup() {
        final String huge = "Word ".repeat(99) + "End. ";
        final String text = "Short one here. " + huge + "Short two here.";

        final OversizedSplit.Plan plan = OversizedSplit.planByWords(segmentOf(text), text, "en", GROUP_WORDS, SPLITTER);

        assertThat(((OversizedSplit.Pieces) plan).pieces())
                .containsExactly("Short one here. ", huge, "Short two here.");
    }

    @Test
    void planByWords_oneSentenceOnly_isUnsplittable() {
        final String text = "word ".repeat(199) + "end.";

        final OversizedSplit.Plan plan = OversizedSplit.planByWords(segmentOf(text), text, "en", GROUP_WORDS, SPLITTER);

        assertThat(plan).isInstanceOf(OversizedSplit.Unsplittable.class);
    }

    // The tokens of the shown text are no words, and a pair never lands in two groups unless a sentence boundary lies
    // inside it, which the splitter already refuses.
    @Test
    void planByWords_tokensInTheText_areNotCountedAsWords() {
        final String sentence = "Start ⟦g0⟧mid⟦g1⟧ " + "word ".repeat(42) + "end. ";
        final String text = sentence.repeat(4).strip();

        final OversizedSplit.Plan plan = OversizedSplit.planByWords(segmentOf(text), text, "en", GROUP_WORDS, SPLITTER);

        assertThat(((OversizedSplit.Pieces) plan).pieces()).hasSize(2);
    }

    private static Segment segmentOf(final String masked) {
        return new Segment(
                "Book.md:0",
                "Book.md",
                0,
                SegmentKind.PARAGRAPH,
                masked,
                masked,
                java.util.Map.of(),
                "hash",
                null,
                null,
                new ByteSpanAnchor(0, masked.length()),
                null,
                SegmentStatus.PENDING,
                0.0);
    }

    private static String paragraph(final String id) throws IOException {
        return paragraphs(id).getFirst();
    }

    private static List<String> paragraphs() throws IOException {
        return paragraphs("");
    }

    private static List<String> paragraphs(final String idPrefix) throws IOException {
        try (InputStream in = OversizedSplitGroupsTest.class.getResourceAsStream("/eval/long-paragraphs.json")) {
            final JsonNode all = new ObjectMapper().readTree(Objects.requireNonNull(in));
            return all.findParents("text").stream()
                    .filter(node -> node.get("id").asText().startsWith(idPrefix))
                    .map(node -> node.get("text").asText())
                    .toList();
        }
    }
}

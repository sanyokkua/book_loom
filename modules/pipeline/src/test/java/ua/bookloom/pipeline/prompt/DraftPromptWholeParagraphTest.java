package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.pipeline.PromptSection;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;

/** The whole-paragraph block of the sentence-group arm (15i.X3) is optional: absent, the prompt is what it was. */
class DraftPromptWholeParagraphTest {

    private static final String PLAIN_USER = """
            Translate from English (en) to Ukrainian (uk).

            <Text>
            Hello.
            </Text>

            Reminder: the target is written in Ukrainian (uk), not in English (en); never copy the text above and never write "...".
            Return exactly one JSON object matching this schema: {"target":"<translation>"}""";

    private static final String WITH_PARAGRAPH_USER = """
            [The whole paragraph — context only; do NOT re-translate it. The text to translate is one part of it]
            <WholeParagraph>
            Hello. It was a fine day.
            </WholeParagraph>

            Translate from English (en) to Ukrainian (uk).

            <Text>
            Hello.
            </Text>

            Reminder: the target is written in Ukrainian (uk), not in English (en); never copy the text above and never write "...".
            Return exactly one JSON object matching this schema: {"target":"<translation>"}""";

    @Test
    void messagesFor_noWholeParagraph_isByteEqualToThePromptWithoutTheBlock() {
        final List<ChatMessage> messages = builder().messagesFor(segment("Hello."), DraftContext.empty());

        assertThat(messages.get(1).content()).isEqualTo(PLAIN_USER);
    }

    @Test
    void messagesFor_wholeParagraph_showsItBeforeTheTranslateLine() {
        final DraftContext context = DraftContext.empty().withWholeParagraph("Hello. It was a fine day.");

        final List<ChatMessage> messages = builder().messagesFor(segment("Hello."), context);

        assertThat(messages.get(1).content()).isEqualTo(WITH_PARAGRAPH_USER);
    }

    @Test
    void messagesFor_blankWholeParagraph_leavesTheBlockOut() {
        final DraftContext context = DraftContext.empty().withWholeParagraph("  ");

        final List<ChatMessage> messages = builder().messagesFor(segment("Hello."), context);

        assertThat(messages.get(1).content()).isEqualTo(PLAIN_USER);
    }

    @Test
    void messagesFor_wholeParagraph_leavesTheSystemMessageAsItWas() {
        final DraftContext context = DraftContext.empty().withWholeParagraph("Hello. It was a fine day.");

        final String without = builder()
                .messagesFor(segment("Hello."), DraftContext.empty())
                .getFirst()
                .content();
        final String with =
                builder().messagesFor(segment("Hello."), context).getFirst().content();

        assertThat(with).isEqualTo(without);
    }

    @Test
    void sectionsFor_wholeParagraph_namesTheBlockAmongTheUserSections() {
        final DraftContext context = DraftContext.empty().withWholeParagraph("Hello. It was a fine day.");

        final var sections = builder().sectionsFor(DraftStep.DRAFT, context, "Hello.", "", "", "");

        assertThat(sections).extracting(PromptSection::slot).contains("wholeParagraph");
    }

    private static DraftPromptBuilder builder() {
        final BookBrief brief = BookBrief.defaults("en");
        return new DraftPromptBuilder(
                new PromptTemplates(), new CallFrame("en", "uk", StyleSheet.from(brief), ForeignPassagePolicy.KEEP));
    }

    private static Segment segment(final String masked) {
        return new Segment(
                "Book.md:0",
                "Book.md",
                0,
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
                0.0);
    }
}

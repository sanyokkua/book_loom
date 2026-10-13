package ua.bookloom.pipeline.batch;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.pipeline.prompt.BatchNotesSwitch;
import ua.bookloom.pipeline.prompt.BatchSchema;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.DraftContext;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;

/**
 * The notes arm (15i.X2) in what a batch asks: with the switch on the schema puts {@code notes} before {@code target}
 * in every item and the prompt asks for them; with it off the schema and the prompt are exactly today's.
 */
class BatchNotesPromptTest {

    // The schema as it stood before the notes arm: the off state must send these exact bytes.
    private static final String TODAYS_SCHEMA = "{\"type\":\"object\",\"properties\":{\"items\":{\"type\":\"array\","
            + "\"items\":{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"string\"},\"target\":{\"type\":"
            + "\"string\"},\"terms\":{\"type\":\"object\"}},\"required\":[\"id\",\"target\"]}}},\"required\":"
            + "[\"items\"]}";
    private static final List<BatchItem> ITEMS =
            List.of(new BatchItem("1", "She opened the door."), new BatchItem("2", "He left."));

    @AfterEach
    void clearProperty() {
        System.clearProperty(BatchNotesSwitch.PROPERTY_NAME);
    }

    private static BatchPromptBuilder builder() {
        return new BatchPromptBuilder(
                new PromptTemplates(),
                new CallFrame("en", "uk", StyleSheet.from(BookBrief.defaults("en")), ForeignPassagePolicy.KEEP),
                BatchFormat.JSON);
    }

    private static ChatRequest request() {
        final BatchPromptBuilder builder = builder();
        final List<ChatMessage> messages = builder.messagesFor(
                new BatchContext(DraftContext.empty(), List.of(), List.of(), null, List.of(), List.of()), ITEMS);
        return builder.requestFor(messages, ITEMS, false);
    }

    private static String schemaSent() {
        return Objects.requireNonNull(request().responseFormat(), "responseFormat")
                .jsonSchema();
    }

    private static String prompt() {
        return builder()
                .messagesFor(
                        new BatchContext(DraftContext.empty(), List.of(), List.of(), null, List.of(), List.of()), ITEMS)
                .stream()
                .map(ChatMessage::content)
                .collect(Collectors.joining("\n"));
    }

    private static List<String> itemKeys(final String schema) throws IOException {
        final JsonNode properties = new ObjectMapper()
                .readTree(schema)
                .path("properties")
                .path("items")
                .path("items")
                .path("properties");
        final List<String> keys = new ArrayList<>();
        properties.fieldNames().forEachRemaining(keys::add);
        return keys;
    }

    @Test
    void requestFor_switchOn_putsNotesBeforeTargetInEveryItem() throws IOException {
        System.setProperty(BatchNotesSwitch.PROPERTY_NAME, "on");

        assertThat(itemKeys(schemaSent())).containsExactly("id", "notes", "target", "terms");
    }

    // A reply with no notes must stay a valid answer to the schema the model was given.
    @Test
    void requestFor_switchOn_keepsNotesOptional() throws IOException {
        System.setProperty(BatchNotesSwitch.PROPERTY_NAME, "on");

        final JsonNode required = new ObjectMapper()
                .readTree(schemaSent())
                .path("properties")
                .path("items")
                .path("items")
                .path("required");

        assertThat(required).extracting(JsonNode::asText).containsExactly("id", "target");
    }

    @Test
    void requestFor_switchOff_sendsTodaysSchemaExactly() {
        assertThat(schemaSent()).isEqualTo(TODAYS_SCHEMA);
        assertThat(BatchSchema.SCHEMA).isEqualTo(TODAYS_SCHEMA);
    }

    @Test
    void messagesFor_switchOn_asksForAOneLineNoteBeforeEachTarget() {
        System.setProperty(BatchNotesSwitch.PROPERTY_NAME, "on");

        assertThat(prompt()).contains("\"notes\"");
    }

    @Test
    void messagesFor_switchOff_doesNotMentionNotes() {
        assertThat(prompt()).doesNotContainIgnoringCase("notes");
    }
}

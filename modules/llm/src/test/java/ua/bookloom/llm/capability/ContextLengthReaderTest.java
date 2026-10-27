package ua.bookloom.llm.capability;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ContextLengthReaderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void fromOllamaPs_matchingModel_returnsItsContextLength() {
        final String body = "{\"models\":[{\"name\":\"other:1b\",\"context_length\":2048},"
                + "{\"name\":\"gemma4:e4b\",\"model\":\"gemma4:e4b\",\"context_length\":16384}]}";

        assertThat(ContextLengthReader.fromOllamaPs(body, "gemma4:e4b", MAPPER)).contains(16384);
    }

    @Test
    void fromOllamaPs_matchByModelField_returnsItsContextLength() {
        final String body = "{\"models\":[{\"name\":\"alias\",\"model\":\"gemma4:e4b\",\"context_length\":8192}]}";

        assertThat(ContextLengthReader.fromOllamaPs(body, "gemma4:e4b", MAPPER)).contains(8192);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "not json",
                "{}",
                "{\"models\":[]}",
                "{\"models\":[{\"name\":\"gemma4:e4b\"}]}",
                "{\"models\":[{\"name\":\"gemma4:e4b\",\"context_length\":0}]}"
            })
    void fromOllamaPs_notJsonOrNoUsableEntry_isEmpty(final String body) {
        assertThat(ContextLengthReader.fromOllamaPs(body, "gemma4:e4b", MAPPER)).isEmpty();
    }
}

package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BatchExamplesTest {

    private static final String RAW = "Hi. ⇒ Привіт.\n1881 ⇒ 1881\n\nNo. ⇒ Ні.";

    @Test
    void render_json_numbersItemsAndWritesOneObjectPerExample() {
        assertThat(BatchExamples.render(RAW))
                .isEqualTo("<Items>\n<s id=\"1\">Hi.</s>\n<s id=\"2\">1881</s>\n</Items>\n"
                        + "Reply: {\"items\":[{\"id\":\"1\",\"target\":\"Привіт.\"},{\"id\":\"2\",\"target\":\"1881\"}]}"
                        + "\n\n<Items>\n<s id=\"1\">No.</s>\n</Items>\n"
                        + "Reply: {\"items\":[{\"id\":\"1\",\"target\":\"Ні.\"}]}");
    }

    @Test
    void render_jsonTargetWithAQuote_isEscaped() {
        assertThat(BatchExamples.render("Say \"hi\". ⇒ Скажи \"привіт\"."))
                .contains("\"target\":\"Скажи \\\"привіт\\\".\"");
    }

    @Test
    void render_lineWithoutArrow_isRejected() {
        assertThatThrownBy(() -> BatchExamples.render("no arrow here")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void schema_batchJson_isFlatWithoutLengthLimits() {
        assertThat(BatchSchema.SCHEMA).doesNotContain("maxItems", "maxLength", "additionalProperties");
    }
}

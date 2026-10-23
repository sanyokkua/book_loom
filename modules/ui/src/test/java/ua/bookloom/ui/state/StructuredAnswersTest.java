package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StructuredAnswersTest {

    private final StructuredAnswers answers = new StructuredAnswers();

    @Test
    void lookup_nothingNoted_isEmpty() {
        assertThat(answers.lookup("ollama", "gemma4:e4b")).isEmpty();
    }

    @Test
    void record_yesAndNo_areKeptPerProviderAndModel() {
        answers.record("ollama", "gemma4:e4b", true);
        answers.record("ollama", "gpt-oss", false);

        assertThat(answers.lookup("ollama", "gemma4:e4b")).contains(true);
        assertThat(answers.lookup("ollama", "gpt-oss")).contains(false);
        assertThat(answers.lookup("lmstudio", "gemma4:e4b")).isEmpty();
    }

    @Test
    void record_again_replacesTheEarlierAnswerAndCountsARevision() {
        answers.record("ollama", "gpt-oss", false);
        final long before = answers.revision().get();

        answers.record("ollama", "gpt-oss", true);

        assertThat(answers.lookup("ollama", "gpt-oss")).contains(true);
        assertThat(answers.revision().get()).isGreaterThan(before);
    }
}

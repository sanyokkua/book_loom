package ua.bookloom.llm.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Whether a model runs on the provider's hardware: decided by the address and by a cloud model's own name. */
class ModelHostingTest {

    @ParameterizedTest
    @CsvSource({
        "http://localhost:11434, gemma4:e4b-mlx, false",
        "http://127.0.0.1:1234/v1, qwen3:8b, false",
        "http://[::1]:8080, qwen3:8b, false",
        "http://192.168.1.20:11434, qwen3:8b, false",
        "http://10.0.0.5:8000/v1, qwen3:8b, false",
        "http://172.20.1.1:8000/v1, qwen3:8b, false",
        "http://workstation:11434, qwen3:8b, false",
        "http://studio.local:11434, qwen3:8b, false",
        "https://api.openai.com/v1, gpt-5, true",
        "https://integrate.api.nvidia.com/v1, meta/llama-3.2-90b-vision-instruct, true",
        "https://openrouter.ai/api/v1, openrouter/free, true",
        "https://ollama.com, gpt-oss:120b, true",
        "http://127.0.0.1:11434, gpt-oss:120b-cloud, true",
        "http://127.0.0.1:11434, deepseek-v4.1-flash:cloud, true"
    })
    void isHosted_addressAndModel_isTheExpected(final String address, final String model, final boolean expected) {
        assertThat(ModelHosting.isHosted(URI.create(address), model)).isEqualTo(expected);
    }
}

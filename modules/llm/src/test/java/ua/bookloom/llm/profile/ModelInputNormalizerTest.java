package ua.bookloom.llm.profile;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.llm.ModelInput;
import ua.bookloom.api.llm.ModelInputForm;

class ModelInputNormalizerTest {

    private final ModelInputNormalizer normalizer = new ModelInputNormalizer();

    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "https://huggingface.co/unsloth/GLM-4.7-Flash-GGUF?show_file_info=GLM-4.7-Flash-UD-Q4_K_XL.gguf"
                        + "|HUGGING_FACE_URL|unsloth/GLM-4.7-Flash-GGUF|unsloth/GLM-4.7-Flash-GGUF"
                        + "|GLM-4.7-Flash-UD-Q4_K_XL.gguf|q4_k_xl|true",
                "https://huggingface.co/unsloth/GLM-4.7-Flash-GGUF|HUGGING_FACE_URL|unsloth/GLM-4.7-Flash-GGUF"
                        + "|unsloth/GLM-4.7-Flash-GGUF|||false",
                "https://huggingface.co/unsloth/Qwen3-8B-GGUF/blob/main/Qwen3-8B-Q4_K_M.gguf|HUGGING_FACE_URL"
                        + "|unsloth/Qwen3-8B-GGUF|unsloth/Qwen3-8B-GGUF|Qwen3-8B-Q4_K_M.gguf|q4_k_m|false",
                "huggingface.co/unsloth/Qwen3-8B-GGUF|HUGGING_FACE_URL|unsloth/Qwen3-8B-GGUF"
                        + "|unsloth/Qwen3-8B-GGUF|||false",
                "https://ollama.com/library/gemma3:4b|OLLAMA_URL|gemma3:4b||||false",
                "ollama run hf.co/unsloth/Mistral-Small-3.2-24B-Instruct-2506-GGUF:IQ4_NL|COMMAND"
                        + "|hf.co/unsloth/Mistral-Small-3.2-24B-Instruct-2506-GGUF:IQ4_NL"
                        + "|unsloth/Mistral-Small-3.2-24B-Instruct-2506-GGUF||iq4_nl|false",
                "ollama pull gemma4:e4b|COMMAND|gemma4:e4b||||false",
                "lms get qwen/qwen3.8-27b@4bit|COMMAND|qwen/qwen3.8-27b@4bit|||4bit|false",
                "llama-server -hf ggml-org/gpt-oss-20b-GGUF --jinja -c 8192|COMMAND|ggml-org/gpt-oss-20b-GGUF"
                        + "|ggml-org/gpt-oss-20b-GGUF|||false",
                "NousResearch_Hermes-4-14B-Q4_K_L.gguf|FILE|NousResearch_Hermes-4-14B-Q4_K_L.gguf"
                        + "||NousResearch_Hermes-4-14B-Q4_K_L.gguf|q4_k_l|false",
                "/models/Qwen3-8B-Q8_0.gguf|FILE|Qwen3-8B-Q8_0.gguf||Qwen3-8B-Q8_0.gguf|q8_0|false",
                "hf.co/bartowski/Llama-3.2-3B-Instruct-GGUF:IQ3_M|MODEL_ID"
                        + "|hf.co/bartowski/Llama-3.2-3B-Instruct-GGUF:IQ3_M|bartowski/Llama-3.2-3B-Instruct-GGUF"
                        + "||iq3_m|false",
                "'  gemma4:e4b-it-q4_K_M  '|MODEL_ID|gemma4:e4b-it-q4_K_M|||q4_k_m|false",
                "someuser/mymodel:7b-q4_K_M|MODEL_ID|someuser/mymodel:7b-q4_K_M|||q4_k_m|false",
            })
    void normalise_pastedForms_yieldsTheModelIdToUse(
            final String pasted,
            final ModelInputForm form,
            final String modelId,
            final String repo,
            final String file,
            final String quant,
            final boolean dynamic) {
        final ModelInput input = normalizer.normalise(pasted);

        assertThat(input.form()).isEqualTo(form);
        assertThat(input.modelId()).isEqualTo(modelId);
        assertThat(input.repo()).isEqualTo(repo);
        assertThat(input.file()).isEqualTo(file);
        assertThat(input.quant()).isEqualTo(quant);
        assertThat(input.dynamic()).isEqualTo(dynamic);
    }
}

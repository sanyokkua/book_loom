package ua.bookloom.llm.profile;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ua.bookloom.api.llm.ModelIdentity;
import ua.bookloom.api.llm.ModelKind;

class ModelIdentityParserTest {

    private final ModelIdentityParser parser = new ModelIdentityParser();

    @Test
    void parse_unknownUserModel_keepsItsOwnWordsAndInventsNoFamily() {
        final ParsedName parsed = parser.parse("someuser/mymodel:7b-q4_K_M");

        assertThat(parsed.identity().family()).isEqualTo("mymodel");
        assertThat(parsed.identity().sizeB()).isEqualTo(7.0);
        assertThat(parsed.identity().baseFamily()).isNull();
        assertThat(parsed.facts().quant()).isEqualTo("q4_k_m");
    }

    @Test
    void parse_dynamicQuantFile_keepsTheLabelAsWritten() {
        final ModelIdentity identity =
                parser.parse("GLM-4.7-Flash-UD-Q4_K_XL.gguf").identity();

        assertThat(identity.quantLabel()).isEqualTo("UD-Q4_K_XL");
        assertThat(identity.kind()).isEqualTo(ModelKind.LLM);
        assertThat(identity.family()).isEqualTo("glm");
    }
}

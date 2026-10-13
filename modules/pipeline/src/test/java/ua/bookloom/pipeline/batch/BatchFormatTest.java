package ua.bookloom.pipeline.batch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The batch format has a three-way switch the prompt eval flips to decide it (15i.X1): JSON items until the gate says
 * otherwise, set by {@code BOOKLOOM_BATCH_FORMAT} or the {@code bookloom.batch.format} property, the eval passing
 * {@code BOOKLOOM_EVAL_BATCH_FORMAT} through as that property.
 */
class BatchFormatTest {

    @AfterEach
    void clearProperty() {
        System.clearProperty(BatchFormat.PROPERTY_NAME);
    }

    @Test
    void default_untilTheGateDecides_isJson() {
        assertThat(BatchFormat.DEFAULT).isEqualTo(BatchFormat.JSON);
    }

    @Test
    void names_areTheEnvironmentVariableAndTheProperty() {
        assertThat(BatchFormat.ENV_NAME).isEqualTo("BOOKLOOM_BATCH_FORMAT");
        assertThat(BatchFormat.PROPERTY_NAME).isEqualTo("bookloom.batch.format");
    }

    // The environment wins over the property; a name that is none of the three keeps the default.
    @ParameterizedTest
    @CsvSource(
            value = {
                "null,null,JSON",
                "json,null,JSON",
                "markers,null,MARKERS",
                "MARKERS,null,MARKERS",
                "' dual ',null,DUAL",
                "null,markers,MARKERS",
                "null,Dual,DUAL",
                "dual,markers,DUAL",
                "json,markers,JSON",
                "markers,json,MARKERS",
                "xml,null,JSON",
                "null,xml,JSON",
                "xml,markers,JSON"
            },
            nullValues = "null")
    void resolve_envThenProperty_namesTheFormat(final String env, final String property, final BatchFormat expected) {
        final Map<String, String> environment = env == null ? Map.of() : Map.of(BatchFormat.ENV_NAME, env);

        assertThat(BatchFormat.resolve(environment, property)).isEqualTo(expected);
    }

    @Test
    void current_propertyMarkers_isMarkers() {
        System.setProperty(BatchFormat.PROPERTY_NAME, "markers");

        assertThat(BatchFormat.current()).isEqualTo(BatchFormat.MARKERS);
    }

    @Test
    void current_propertyDual_isDual() {
        System.setProperty(BatchFormat.PROPERTY_NAME, "dual");

        assertThat(BatchFormat.current()).isEqualTo(BatchFormat.DUAL);
    }

    @Test
    void current_propertyCleared_isTheDefault() {
        assertThat(BatchFormat.current()).isEqualTo(BatchFormat.DEFAULT);
    }
}

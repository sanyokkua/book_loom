package ua.bookloom.pipeline.checks;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptTemplates;

/** Chooses the word validator an eval harness runs with; the application's runs never read this switch. */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class WordValidators {

    /** The system property (or, upper-cased with underscores, the environment variable) that selects the model. */
    public static final String SWITCH = "bookloom.eval.wordvalidator";

    private static final String MODEL = "model";

    /**
     * The validator an eval switch names.
     *
     * @param mode the switch value, or null when unset
     * @param templates the prompt templates; never null
     * @param mapper the tolerant JSON mapper; never null
     * @param calls the seam every model call goes through; never null
     * @param frame the language pair of the system message; never null
     * @return the {@link ModelWordValidator} when the switch reads "model" in any letter case, else {@link WordValidator#none()}
     */
    public static WordValidator forMode(
            @Nullable final String mode,
            final PromptTemplates templates,
            final ObjectMapper mapper,
            final ModelCalls calls,
            final CallFrame frame) {
        Objects.requireNonNull(templates, "templates");
        final boolean model = MODEL.equalsIgnoreCase(mode);
        log.debug("Word validator switch={} model={}", mode, model);
        return model ? new ModelWordValidator(templates, mapper, calls, frame) : WordValidator.none();
    }
}

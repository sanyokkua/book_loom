package ua.bookloom.pipeline.eval;

import com.google.inject.Guice;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.PromptVariants;
import ua.bookloom.pipeline.prompt.PromptVariants.LanguageRulesVariant;

/**
 * The prompt variants an eval run asks for, read from {@value #ENV} ({@code languageRules=GENERIC_ONLY}, fields
 * separated by commas; unset is the defaults) and handed to the templates through a test injector's binding. Only the
 * eval reads this variable; the application's injector always binds {@link PromptVariants#DEFAULTS}.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EvalVariants {

    static final String ENV = "BOOKLOOM_EVAL_VARIANTS";

    /**
     * Parses the variants a {@value #ENV} value names.
     *
     * @param env the non-null environment
     * @return the defaults with each named field replaced
     * @throws IllegalArgumentException when a field or value is unknown, so a typo never runs the default silently
     */
    static PromptVariants fromEnvironment(final Map<String, String> env) {
        final String asked =
                Objects.requireNonNull(env, "env").getOrDefault(ENV, "").strip();
        PromptVariants variants = PromptVariants.DEFAULTS;
        for (final String part : Arrays.stream(asked.split(","))
                .map(String::strip)
                .filter(text -> !text.isEmpty())
                .toList()) {
            variants = with(part);
        }
        log.debug("Eval variants asked={} bound={}", asked, variants);
        return variants;
    }

    /** The bundled templates as a test injector that binds the environment's variants builds them. */
    public static PromptTemplates templates() {
        final PromptVariants variants = fromEnvironment(System.getenv());
        return Guice.createInjector(binder -> binder.bind(PromptVariants.class).toInstance(variants))
                .getInstance(PromptTemplates.class);
    }

    private static PromptVariants with(final String field) {
        final String[] pair = field.split("=", 2);
        if (pair.length != 2) {
            throw new IllegalArgumentException(ENV + " wants field=VALUE, not " + field);
        }
        final String value = pair[1].strip();
        return switch (pair[0].strip()) {
            case "languageRules" -> new PromptVariants(LanguageRulesVariant.valueOf(value));
            default -> throw new IllegalArgumentException(ENV + " names no field " + pair[0]);
        };
    }
}

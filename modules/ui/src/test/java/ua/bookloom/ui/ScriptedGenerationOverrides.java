package ua.bookloom.ui;

import java.util.Optional;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.settings.GenerationOverrides;

/** Says which variable or property decides a Generation setting, as a test tells it to. */
public final class ScriptedGenerationOverrides implements GenerationOverrides {

    private final @Nullable String reasoning;
    private final @Nullable String sampling;

    private ScriptedGenerationOverrides(final @Nullable String reasoning, final @Nullable String sampling) {
        this.reasoning = reasoning;
        this.sampling = sampling;
    }

    /** Nothing overrides: the stored choices decide. */
    public static ScriptedGenerationOverrides none() {
        return new ScriptedGenerationOverrides(null, null);
    }

    /** The thinking level is decided by {@code source}. */
    public static ScriptedGenerationOverrides reasoningFrom(final String source) {
        return new ScriptedGenerationOverrides(source, null);
    }

    /** The sampling mode is decided by {@code source}. */
    public static ScriptedGenerationOverrides samplingFrom(final String source) {
        return new ScriptedGenerationOverrides(null, source);
    }

    @Override
    public Optional<String> reasoning() {
        return Optional.ofNullable(reasoning);
    }

    @Override
    public Optional<String> sampling() {
        return Optional.ofNullable(sampling);
    }
}

package ua.bookloom.llm.profile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ModelIdentity;

/** The person-facing name of a model, built from its identity ("GLM 4.7 Flash"); shared by every screen that shows one. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ModelLabels {

    private static final Set<String> ACRONYMS = Set.of("glm", "gpt", "oss", "vl", "moe", "lfm", "olmo", "smol");

    /**
     * Builds the label.
     *
     * @param id who the model is; never {@code null}
     * @return family, version and line as words, or an empty text when the name gave no family
     */
    public static String of(final ModelIdentity id) {
        Objects.requireNonNull(id, "id");
        final String family = id.family();
        if (family == null) {
            return "";
        }
        final List<String> words = new ArrayList<>();
        for (final String word : family.split("-", -1)) {
            words.add(word(word));
        }
        add(words, id.version());
        add(words, id.line());
        return String.join(" ", words);
    }

    /**
     * Builds the label with the model's size, such as {@code Hermes 4 70B} or {@code Gemma 4 E4B}.
     *
     * @param id who the model is; never {@code null}
     * @return {@link #of} followed by the effective or the total size when the name gave one
     */
    public static String withSize(final ModelIdentity id) {
        final String label = of(id);
        final Double effective = id.effB();
        final Double total = id.sizeB();
        if (effective != null) {
            return (label + " E" + billions(effective) + "B").strip();
        }
        return total == null ? label : (label + " " + billions(total) + "B").strip();
    }

    private static String billions(final double size) {
        return size == Math.rint(size) ? String.valueOf((long) size) : String.valueOf(size);
    }

    private static void add(final List<String> words, final @Nullable String text) {
        if (text != null && !text.isBlank()) {
            words.add(word(text));
        }
    }

    private static String word(final String text) {
        if (ACRONYMS.contains(text.toLowerCase(Locale.ROOT))) {
            return text.toUpperCase(Locale.ROOT);
        }
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}

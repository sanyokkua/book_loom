package ua.bookloom.llm.profile;

import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Builds the {@code ollama pull} line for a pasted Hugging Face model; it is shown as text and never run. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PullCommand {

    private static final String HUB_PREFIX = "ollama pull hf.co/";

    /**
     * The quant label is taken as written in the paste ("UD-Q4_K_XL"), because Ollama's tag is case-sensitive and the
     * normalised quant on the paste record is lower-cased without the dynamic prefix.
     *
     * @param parsed the parsed paste; never {@code null}
     * @return the command, or empty when the paste named no repository or no quant
     */
    public static Optional<String> of(final ParsedName parsed) {
        Objects.requireNonNull(parsed, "parsed");
        final String repo = parsed.origin().repo();
        final String quant = parsed.identity().quantLabel();
        if (repo == null || quant == null) {
            return Optional.empty();
        }
        return Optional.of(HUB_PREFIX + repo + ":" + quant);
    }
}

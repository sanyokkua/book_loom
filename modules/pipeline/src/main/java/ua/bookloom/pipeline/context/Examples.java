package ua.bookloom.pipeline.context;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ModelProfile;
import ua.bookloom.pipeline.prompt.FrameWording;
import ua.bookloom.pipeline.prompt.LanguageRules;

/**
 * Chooses block 6 (design D9): the pair file's own examples, else the target file's illustration under its label, else
 * none. The generic file's examples are never shown here, since a model copies whatever example it is given and a
 * language-neutral one teaches nothing of the target; a profile with {@link ModelProfile.Quirk#EXAMPLES_OFF} gets none.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Examples {

    static final String LABEL_KEY = "illustration.label";

    /**
     * The examples one run shows.
     *
     * @param rules the non-null language-rules map
     * @param sourceTag the source language tag, or null when it is inferred, which has no pair file
     * @param targetTag the non-null target language tag
     * @param profile the non-null model profile
     * @return the choice; never null
     */
    public static ExampleChoice choose(
            final LanguageRules rules,
            @Nullable final String sourceTag,
            final String targetTag,
            final ModelProfile profile) {
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(targetTag, "targetTag");
        Objects.requireNonNull(profile, "profile");
        if (profile.quirks().contains(ModelProfile.Quirk.EXAMPLES_OFF)) {
            log.debug("Examples {} -> {}: none, the profile turns examples off", sourceTag, targetTag);
            return ExampleChoice.NONE;
        }
        final String pair = FrameWording.pairExamples(rules, sourceTag, targetTag);
        if (!pair.isBlank()) {
            log.debug("Examples {} -> {}: the pair's own, {} chars", sourceTag, targetTag, pair.length());
            return new ExampleChoice(ExampleChoice.Kind.PAIR, pair);
        }
        final String illustration = FrameWording.illustration(rules, targetTag);
        if (illustration.isBlank()) {
            log.debug("Examples {} -> {}: none, no pair examples and no target illustration", sourceTag, targetTag);
            return ExampleChoice.NONE;
        }
        final String label = FrameWording.wording(rules, targetTag, LABEL_KEY)
                .replace("{target}", FrameWording.englishName(targetTag));
        log.debug(
                "Examples {} -> {}: the target's illustration, {} chars", sourceTag, targetTag, illustration.length());
        return new ExampleChoice(ExampleChoice.Kind.TARGET, label + "\n" + illustration);
    }
}
